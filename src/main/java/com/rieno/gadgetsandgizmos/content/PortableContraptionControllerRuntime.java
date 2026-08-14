package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// Keep portable controller input attached to its target and safely releases stale sessions
public final class PortableContraptionControllerRuntime {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Map<UUID, List<State>> ACTIVE = new HashMap<>();
    private static final Map<LecternKey, State> LECTERN_ACTIVE = new HashMap<>();
    private static final Map<LecternKey, Boolean> PENDING_LECTERN_ACTIVATIONS = new LinkedHashMap<>();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the portable contraption controller
    private PortableContraptionControllerRuntime() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Set the active
    public static void setActive(ServerPlayer player, InteractionHand hand, boolean advanced, boolean active) {
        if (player == null || hand == null || !active) {
            return;
        }
        State state = getOrCreateHandState(player, hand, advanced);
        if (state != null) {
            tickActiveController(player, state);
        }
    }

    // Handle the portable controller key
    public static void handleKey(ServerPlayer player, InteractionHand hand, boolean advanced,
                                 String channelId, boolean pressed) {
        if (player == null || hand == null) {
            return;
        }
        State state = getOrCreateHandState(player, hand, advanced);
        if (state == null) {
            return;
        }
        state.controller.handleControllerKeyInput(channelId, pressed);
        tickActiveController(player, state);
    }

    // Handle the mouse input
    public static void handleMouseInput(ServerPlayer player, InteractionHand hand, String input,
                                        double val, boolean active) {
        if (player == null || hand == null) return;
        State state = getOrCreateHandState(player, hand, true);
        if (state == null || !(state.controller instanceof AdvancedContraptionControllerBlockEntity controller)) {
            return;
        }
        controller.handleMouseInput(input, val, active);
        tickActiveController(player, state);
    }

    // Handle the hardware input
    public static void handleHardwareInput(ServerPlayer player, InteractionHand hand, boolean advanced,
                                           Map<String, Double> values) {
        if (player == null || hand == null) {
            return;
        }
        State state = getOrCreateHandState(player, hand, advanced);
        if (state == null) {
            return;
        }
        state.controller.applyHardwareControllerInput(values);
        tickActiveController(player, state);
    }

    // Refresh the active controller from stack
    public static void refreshActiveControllerFromStack(ServerPlayer player, InteractionHand hand, boolean advanced) {
        if (player == null || hand == null || player.level().isClientSide) {
            return;
        }
        ItemStack stack = player.getItemInHand(hand);
        State state = findHandState(player.getUUID(), stack, advanced);
        if (state == null) {
            return;
        }
        state.controller.deactivatePortableOutputs();
        state.controller = PortableContraptionControllerItem.createControllerFromStack(
                stack, player.level(), advanced, player.blockPosition());
        tickActiveController(player, state);
    }

    // Save the active controller to stack
    public static void saveActiveControllerToStack(ServerPlayer player, InteractionHand hand, boolean advanced) {
        if (player == null || hand == null || player.level().isClientSide) {
            return;
        }
        ItemStack stack = player.getItemInHand(hand);
        State state = findHandState(player.getUUID(), stack, advanced);
        if (state != null) {
            PortableContraptionControllerItem.saveControllerToStack(stack, state.controller, advanced);
        }
    }

    // Set the lectern active
    public static void setLecternActive(ServerPlayer player, BlockPos pos, boolean advanced, boolean active) {
        if (player == null || pos == null || !active || !isWithinLecternRange(player, pos)) {
            return;
        }
        activateLectern(player.level(), pos, advanced);
    }

    // Handle the lectern key
    public static void handleLecternKey(ServerPlayer player, BlockPos pos, boolean advanced,
                                        String channelId, boolean pressed) {
        if (player == null || pos == null || !isWithinLecternRange(player, pos)) {
            return;
        }
        activateLectern(player.level(), pos, advanced);
        State state = LECTERN_ACTIVE.get(lecternKey(player.level(), pos));
        if (state == null || state.advanced != advanced) {
            return;
        }
        state.controller.handleControllerKeyInput(channelId, pressed);
        tickActiveLectern(player.level(), pos, state);
    }

    // Handle the lectern mouse input
    public static void handleLecternMouseInput(ServerPlayer player, BlockPos pos, String input,
                                               double val, boolean active) {
        if (player == null || pos == null || !isWithinLecternRange(player, pos)) return;
        activateLectern(player.level(), pos, true);
        State state = LECTERN_ACTIVE.get(lecternKey(player.level(), pos));
        if (state == null || !state.advanced
                || !(state.controller instanceof AdvancedContraptionControllerBlockEntity controller)) {
            return;
        }
        controller.handleMouseInput(input, val, active);
        tickActiveLectern(player.level(), pos, state);
    }

    // Handle the lectern hardware input
    public static void handleLecternHardwareInput(ServerPlayer player, BlockPos pos, boolean advanced,
                                                  Map<String, Double> values) {
        if (player == null || pos == null || !isWithinLecternRange(player, pos)) {
            return;
        }
        activateLectern(player.level(), pos, advanced);
        State state = LECTERN_ACTIVE.get(lecternKey(player.level(), pos));
        if (state == null || state.advanced != advanced) {
            return;
        }
        state.controller.applyHardwareControllerInput(values);
        tickActiveLectern(player.level(), pos, state);
    }

    // Handle the HUD interaction
    public static void handleHudInteraction(ServerPlayer player, BlockPos bindingPos, UUID pairId,
                                            String nodeId, String interactionId,
                                            com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument.Value val) {
        if (player == null || pairId == null || nodeId == null || interactionId == null) {
            return;
        }
        if (bindingPos != null) {
            ItemStack lecternStack = LecternPortableContraptionController.getControllerStack(
                    player.level(), bindingPos);
            if (hasGogglesPair(lecternStack, pairId)) {
                activateLectern(player.level(), bindingPos, true);
                State state = LECTERN_ACTIVE.get(lecternKey(player.level(), bindingPos));
                if (state != null && state.controller instanceof AdvancedContraptionControllerBlockEntity controller) {
                    controller.handleHudInteraction(nodeId, interactionId, val);
                    tickActiveLectern(player.level(), bindingPos, state);
                    return;
                }
            }
        }

        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (!isRuntimeInventorySlot(slot)) {
                continue;
            }
            ItemStack stack = inventory.getItem(slot);
            if (!hasGogglesPair(stack, pairId)) {
                continue;
            }
            State state = findHandState(player.getUUID(), stack, true);
            if (state == null) {
                state = new State(true, PortableContraptionControllerItem.createControllerFromStack(
                        stack, player.level(), true, player.blockPosition()), stack);
                ACTIVE.computeIfAbsent(player.getUUID(), ignored -> new ArrayList<>()).add(state);
            }
            if (state.controller instanceof AdvancedContraptionControllerBlockEntity controller) {
                controller.handleHudInteraction(nodeId, interactionId, val);
                tickActiveController(player, state);
            }
            return;
        }
    }

    // Transfer the controller state to the lectern
    public static void transferToLectern(Level level, BlockPos pos, Player player,
                                         ItemStack sourceStack, boolean advanced) {
        if (level == null || pos == null || level.isClientSide
                || !isMatchingPortable(sourceStack, advanced)) {
            return;
        }

        State state = player instanceof ServerPlayer serverPlayer
                ? detachHandState(serverPlayer.getUUID(), sourceStack, advanced)
                : null;
        if (state == null) {
            state = new State(advanced, PortableContraptionControllerItem.createControllerFromStack(
                    sourceStack, level, advanced, pos), ItemStack.EMPTY);
        } else {
            state.portableStack = ItemStack.EMPTY;
        }
        configLecternCallback(level, pos, state);

        LecternKey key = lecternKey(level, pos);
        PENDING_LECTERN_ACTIVATIONS.remove(key);
        State replaced = LECTERN_ACTIVE.put(key, state);
        if (replaced != null && replaced != state) {
            replaced.controller.deactivatePortableOutputs();
        }
    }

    // Activate the lectern
    public static void activateLectern(Level level, BlockPos pos, boolean advanced) {
        if (level == null || pos == null || level.isClientSide) {
            return;
        }
        LecternKey key = lecternKey(level, pos);
        PENDING_LECTERN_ACTIVATIONS.remove(key);
        if (!level.isLoaded(pos)) {
            return;
        }
        ItemStack stack = LecternPortableContraptionController.getControllerStack(level, pos);
        if (!LecternPortableContraptionController.isMatchingPortable(stack, advanced)) {
            return;
        }

        State state = LECTERN_ACTIVE.get(key);
        if (state != null && state.advanced != advanced) {
            stopLecternState(level, key, state);
            LECTERN_ACTIVE.remove(key);
            state = null;
        }
        if (state == null) {
            AnalogueContraptionControllerBlockEntity controller =
                    LecternPortableContraptionController.createControllerFromLectern(level, pos, advanced);
            if (controller == null) {
                return;
            }
            state = new State(advanced, controller, ItemStack.EMPTY);
            configLecternCallback(level, pos, state);
            LECTERN_ACTIVE.put(key, state);
        }
        tickActiveLectern(level, pos, state);
    }

    // Copy the lectern runtime to stack
    public static void copyLecternRuntimeToStack(Level level, BlockPos pos, ItemStack destination) {
        if (level == null || pos == null || destination == null || destination.isEmpty()) {
            return;
        }
        State state = LECTERN_ACTIVE.get(lecternKey(level, pos));
        if (state != null && isMatchingPortable(destination, state.advanced)) {
            PortableContraptionControllerItem.saveControllerToStack(
                    destination, state.controller, state.advanced);
        }
    }

    // Finish the lectern removal
    public static void finishLecternRemoval(Level level, BlockPos pos,
                                            ServerPlayer player, ItemStack destination) {
        if (level == null || pos == null) {
            return;
        }
        LecternKey key = lecternKey(level, pos);
        PENDING_LECTERN_ACTIVATIONS.remove(key);
        State state = LECTERN_ACTIVE.remove(key);
        if (state == null && destination != null
                && destination.getItem() instanceof PortableContraptionControllerItem portable) {
            state = new State(portable.isAdvanced(), PortableContraptionControllerItem.createControllerFromStack(
                    destination, level, portable.isAdvanced(), pos), ItemStack.EMPTY);
        }
        if (state == null) {
            return;
        }

        if (player != null && isStackInRuntimeSlot(player, destination)) {
            state.portableStack = destination;
            configHandCallback(state);
            ACTIVE.computeIfAbsent(player.getUUID(), ignored -> new ArrayList<>()).add(state);
            tickActiveController(player, state);
            return;
        }

        state.controller.deactivatePortableOutputs();
        if (isMatchingPortable(destination, state.advanced)) {
            PortableContraptionControllerItem.saveControllerToStack(
                    destination, state.controller, state.advanced);
        }
    }

    // Get the lectern local output
    public static int getLecternLocalOutput(Level level, BlockPos pos, Direction side) {
        if (level == null || pos == null || side == null) {
            return -1;
        }
        State state = LECTERN_ACTIVE.get(lecternKey(level, pos));
        return state == null ? -1 : state.controller.getLocalOutputSignal(side);
    }

    // Stop the lectern
    public static void stopLecternAt(Level level, BlockPos pos) {
        if (level == null || pos == null) {
            return;
        }
        LecternKey key = lecternKey(level, pos);
        PENDING_LECTERN_ACTIVATIONS.remove(key);
        State state = LECTERN_ACTIVE.remove(key);
        if (state != null) {
            stopLecternState(level, key, state);
        }
    }

    // Stop every portable controller session for the player
    public static void stopAll(ServerPlayer player) {
        if (player == null) {
            return;
        }
        List<State> states = ACTIVE.remove(player.getUUID());
        if (states == null) {
            return;
        }
        for (State state : states) {
            stopHandState(state);
        }
    }

    // Handle the post-server tick
    public static void postServerTick(ServerTickEvent.Post evt) {
        for (ServerPlayer player : evt.getServer().getPlayerList().getPlayers()) {
            getOrCreateHandState(player, InteractionHand.MAIN_HAND, true);
            getOrCreateHandState(player, InteractionHand.OFF_HAND, true);
        }

        Iterator<Map.Entry<UUID, List<State>>> ownerIterator = ACTIVE.entrySet().iterator();
        while (ownerIterator.hasNext()) {
            Map.Entry<UUID, List<State>> ownerEntry = ownerIterator.next();
            ServerPlayer player = evt.getServer().getPlayerList().getPlayer(ownerEntry.getKey());
            Iterator<State> stateIterator = ownerEntry.getValue().iterator();
            while (stateIterator.hasNext()) {
                State state = stateIterator.next();
                if (player == null || !tickActiveController(player, state)) {
                    stopHandState(state);
                    stateIterator.remove();
                }
            }
            if (ownerEntry.getValue().isEmpty()) {
                ownerIterator.remove();
            }
        }

        Iterator<Map.Entry<LecternKey, State>> lecternIterator = LECTERN_ACTIVE.entrySet().iterator();
        while (lecternIterator.hasNext()) {
            Map.Entry<LecternKey, State> entry = lecternIterator.next();
            Level level = evt.getServer().getLevel(entry.getKey().dimension());
            if (level == null || !tickActiveLectern(level, entry.getKey().pos(), entry.getValue())) {
                stopLecternState(level, entry.getKey(), entry.getValue());
                lecternIterator.remove();
            }
        }
        activatePendingLecterns(evt.getServer());
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the chunk load event
    public static void onChunkLoad(ChunkEvent.Load evt) {
        if (!(evt.getLevel() instanceof ServerLevel level) || !(evt.getChunk() instanceof LevelChunk chunk)) {
            return;
        }
        for (BlockEntity blockEntity : new ArrayList<>(chunk.getBlockEntities().values())) {
            if (!(blockEntity instanceof LecternBlockEntity lectern)
                    || !(lectern.getBook().getItem() instanceof PortableContraptionControllerItem portable)) {
                continue;
            }
            queueLecternActivation(level, lectern.getBlockPos(), portable.isAdvanced());
        }
    }

    // Handle the chunk unload event
    public static void onChunkUnload(ChunkEvent.Unload evt) {
        if (!(evt.getLevel() instanceof ServerLevel level) || !(evt.getChunk() instanceof LevelChunk chunk)) {
            return;
        }
        PENDING_LECTERN_ACTIVATIONS.keySet().removeIf(key -> inChunk(key, level, chunk));
        for (Map.Entry<LecternKey, State> entry : LECTERN_ACTIVE.entrySet()) {
            LecternKey key = entry.getKey();
            if (!inChunk(key, level, chunk)) {
                continue;
            }
            saveLecternState(level, key, entry.getValue());
        }
    }

    // Handle the server stopped event
    public static void onServerStopped(ServerStoppedEvent evt) {
        for (Map.Entry<LecternKey, State> entry : LECTERN_ACTIVE.entrySet()) {
            Level level = evt.getServer().getLevel(entry.getKey().dimension());
            saveLecternState(level, entry.getKey(), entry.getValue());
        }
        ACTIVE.clear();
        LECTERN_ACTIVE.clear();
        PENDING_LECTERN_ACTIVATIONS.clear();
    }

    // Queue the lectern activation
    private static void queueLecternActivation(Level level, BlockPos pos, boolean advanced) {
        PENDING_LECTERN_ACTIVATIONS.put(lecternKey(level, pos), advanced);
    }

    // Activate the pending lecterns
    private static void activatePendingLecterns(MinecraftServer server) {
        if (PENDING_LECTERN_ACTIVATIONS.isEmpty()) {
            return;
        }
        Map<LecternKey, Boolean> pending = new LinkedHashMap<>(PENDING_LECTERN_ACTIVATIONS);
        PENDING_LECTERN_ACTIVATIONS.clear();
        for (Map.Entry<LecternKey, Boolean> entry : pending.entrySet()) {
            Level level = server.getLevel(entry.getKey().dimension());
            if (level != null && level.isLoaded(entry.getKey().pos())) {
                activateLectern(level, entry.getKey().pos(), entry.getValue());
            }
        }
    }

    // Check if the lectern is in the chunk
    private static boolean inChunk(LecternKey key, ServerLevel level, LevelChunk chunk) {
        return key.dimension().equals(level.dimension())
                && key.pos().getX() >> 4 == chunk.getPos().x
                && key.pos().getZ() >> 4 == chunk.getPos().z;
    }

    // Update the active controller
    private static boolean tickActiveController(ServerPlayer player, State state) {
        if (player == null || state == null || player.level().isClientSide) {
            return false;
        }
        ItemStack stack = findRuntimeStack(player, state);
        if (!isMatchingPortable(stack, state.advanced)) {
            return false;
        }
        if (state.controller.getLevel() != player.level()) {
            PortableContraptionControllerItem.saveControllerToStack(stack, state.controller, state.advanced);
            state.controller.deactivatePortableOutputs();
            state.controller = PortableContraptionControllerItem.createControllerFromStack(
                    stack, player.level(), state.advanced, player.blockPosition());
        }
        if (state.controller instanceof PortableAdvancedContraptionControllerBlockEntity portable) {
            if (player.getMainHandItem() == stack || player.getOffhandItem() == stack) {
                portable.trackHoldingPlayer(player);
            } else {
                portable.clearTracking();
            }
        }
        tickController(player.level(), state.controller);
        if (state.controller instanceof AdvancedContraptionControllerBlockEntity advanced
                && player.containerMenu instanceof PortableAdvancedContraptionControllerMenu menu
                && menu.observesPortableStack(stack)) {
            advanced.sendGraphRuntimeDataTo(player, menu.containerId);
        }
        return true;
    }

    // Update the active lectern
    private static boolean tickActiveLectern(Level level, BlockPos pos, State state) {
        if (level == null || pos == null || state == null || level.isClientSide) {
            return false;
        }
        if (!level.isLoaded(pos)) {
            return true;
        }
        ItemStack stack = LecternPortableContraptionController.getControllerStack(level, pos);
        if (!LecternPortableContraptionController.isMatchingPortable(stack, state.advanced)) {
            return false;
        }
        if (state.controller.getLevel() != level) {
            PortableContraptionControllerItem.saveControllerToStack(stack, state.controller, state.advanced);
            state.controller.deactivatePortableOutputs();
            state.controller = PortableContraptionControllerItem.createControllerFromStack(
                    stack, level, state.advanced, pos);
            configLecternCallback(level, pos, state);
        }
        if (state.controller instanceof PortableAdvancedContraptionControllerBlockEntity portable) {
            portable.trackLectern(level, pos);
        }
        tickController(level, state.controller);
        if (state.controller instanceof AdvancedContraptionControllerBlockEntity advanced
                && level.getServer() != null) {
            for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
                if (player.containerMenu instanceof PortableAdvancedContraptionControllerMenu menu
                        && menu.observesLectern(pos)) {
                    advanced.sendGraphRuntimeDataTo(player, menu.containerId);
                }
            }
        }
        return true;
    }

    // Update the controller
    private static void tickController(Level level, AnalogueContraptionControllerBlockEntity controller) {
        if (controller instanceof AdvancedContraptionControllerBlockEntity advancedController) {
            AdvancedContraptionControllerBlockEntity.tickServer(
                    level, advancedController.getBlockPos(), advancedController.getBlockState(), advancedController);
        } else {
            AnalogueContraptionControllerBlockEntity.tickServer(
                    level, controller.getBlockPos(), controller.getBlockState(), controller);
        }
    }

    // Get or create the hand state
    private static State getOrCreateHandState(ServerPlayer player, InteractionHand hand, boolean advanced) {
        ItemStack stack = player.getItemInHand(hand);
        if (!isMatchingPortable(stack, advanced)) {
            return null;
        }
        State state = findHandState(player.getUUID(), stack, advanced);
        if (state != null) {
            return state;
        }
        state = new State(advanced, PortableContraptionControllerItem.createControllerFromStack(
                stack, player.level(), advanced, player.blockPosition()), stack);
        ACTIVE.computeIfAbsent(player.getUUID(), ignored -> new ArrayList<>()).add(state);
        return state;
    }

    // Find the hand state
    private static State findHandState(UUID playerId, ItemStack stack, boolean advanced) {
        List<State> states = ACTIVE.get(playerId);
        if (states == null) {
            return null;
        }
        for (State state : states) {
            if (state.advanced == advanced && state.portableStack == stack) {
                return state;
            }
        }
        return null;
    }

    // Detach the hand state
    private static State detachHandState(UUID playerId, ItemStack stack, boolean advanced) {
        List<State> states = ACTIVE.get(playerId);
        if (states == null) {
            return null;
        }
        Iterator<State> iterator = states.iterator();
        while (iterator.hasNext()) {
            State state = iterator.next();
            if (state.advanced != advanced || state.portableStack != stack) {
                continue;
            }
            iterator.remove();
            if (states.isEmpty()) {
                ACTIVE.remove(playerId);
            }
            return state;
        }
        return null;
    }

    // Check if this is within the lectern range
    private static boolean isWithinLecternRange(ServerPlayer player, BlockPos pos) {
        double range = Math.max(player.blockInteractionRange(), 8.0D);
        return player.distanceToSqr(Vec3.atCenterOf(pos)) <= range * range;
    }

    // Check if the portable is matching
    private static boolean isMatchingPortable(ItemStack stack, boolean advanced) {
        return stack.getItem() instanceof PortableContraptionControllerItem portable
                && portable.isAdvanced() == advanced;
    }

    // Check if this has goggles pair
    private static boolean hasGogglesPair(ItemStack stack, UUID pairId) {
        return isMatchingPortable(stack, true)
                && AdvancedContraptionControllerBlockEntity.controllerDataHasGogglesTrackerPair(
                stack.getOrDefault(DataComponents.BLOCK_ENTITY_DATA, CustomData.EMPTY).getUnsafe(), pairId);
    }

    // Find the runtime stack
    private static ItemStack findRuntimeStack(ServerPlayer player, State state) {
        if (!isMatchingPortable(state.portableStack, state.advanced)) {
            return ItemStack.EMPTY;
        }
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (isRuntimeInventorySlot(slot) && inventory.getItem(slot) == state.portableStack) {
                return state.portableStack;
            }
        }
        return ItemStack.EMPTY;
    }

    // Check if the stack is in the runtime slot
    private static boolean isStackInRuntimeSlot(ServerPlayer player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()) {
            return false;
        }
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (isRuntimeInventorySlot(slot) && inventory.getItem(slot) == stack) {
                return true;
            }
        }
        return false;
    }

    // Check if this is a runtime inventory slot
    static boolean isRuntimeInventorySlot(int slot) {
        return Inventory.isHotbarSlot(slot) || slot == Inventory.SLOT_OFFHAND;
    }

    // Stop the hand state
    private static void stopHandState(State state) {
        if (state == null) {
            return;
        }
        state.controller.deactivatePortableOutputs();
        if (isMatchingPortable(state.portableStack, state.advanced)) {
            PortableContraptionControllerItem.saveControllerToStack(
                    state.portableStack, state.controller, state.advanced);
        }
    }

    // Stop the lectern state
    private static void stopLecternState(Level level, LecternKey key, State state) {
        if (state == null) {
            return;
        }
        state.controller.deactivatePortableOutputs();
        saveLecternState(level, key, state);
    }

    // Save the lectern state
    private static void saveLecternState(Level level, LecternKey key, State state) {
        if (level != null && key != null && state != null && level.isLoaded(key.pos())) {
            LecternPortableContraptionController.saveControllerToLectern(
                    level, key.pos(), state.controller, state.advanced, null);
        }
    }

    // Configure the lectern callback
    private static void configLecternCallback(Level level, BlockPos pos, State state) {
        Runnable cb = () -> LecternPortableContraptionController.notifyOutputNeighbors(level, pos);
        if (state.controller instanceof PortableAdvancedContraptionControllerBlockEntity controller) {
            controller.setChangedCallback(cb);
        } else if (state.controller instanceof PortableAnalogueContraptionControllerBlockEntity controller) {
            controller.setChangedCallback(cb);
        }
    }

    // Configure the hand callback
    private static void configHandCallback(State state) {
        if (state.controller instanceof PortableAdvancedContraptionControllerBlockEntity controller) {
            controller.setChangedCallback(null);
        } else if (state.controller instanceof PortableAnalogueContraptionControllerBlockEntity controller) {
            controller.setChangedCallback(null);
        }
    }

    // Get the lectern key
    private static LecternKey lecternKey(Level level, BlockPos pos) {
        return new LecternKey(level.dimension(), pos.immutable());
    }

    // Store the lectern key
    private record LecternKey(ResourceKey<Level> dimension, BlockPos pos) {
    }

    // Store the current state
    private static final class State {
        // Tracks whether advanced is set
        private final boolean advanced;
        // Current state controller
        private AnalogueContraptionControllerBlockEntity controller;
        // Current portable stack
        private ItemStack portableStack;

        // Initialize the state
        private State(boolean advanced, AnalogueContraptionControllerBlockEntity controller, ItemStack portableStack) {
            this.advanced = advanced;
            this.controller = controller;
            this.portableStack = portableStack;
        }
    }
}
