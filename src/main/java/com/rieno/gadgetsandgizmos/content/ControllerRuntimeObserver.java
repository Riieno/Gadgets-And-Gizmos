package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.registry.CTItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

// Share bounded player equipment, menu and goggles-wearer indexes across controller runtimes
final class ControllerRuntimeObserver {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final long EQUIPMENT_SNAPSHOT_TICKS = 5L;
    private static final long WEARER_SNAPSHOT_TICKS = 5L;
    private static final WeakHashMap<MinecraftServer, ServerObserverCache> SERVER_CACHES =
            new WeakHashMap<>();
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Tracks whether curios are initialized
    private static boolean curiosInitialized;
    // Resolved curios inventory method
    private static Method curiosInventoryMethod;
    // Shared curios inventory class
    private static Class<?> curiosInventoryClass;
    // Resolved curios find method
    private static Method curiosFindMethod;
    // Tracks whether accessories are initialized
    private static boolean accessoriesInitialized;
    // Resolved accessories get method
    private static Method accessoriesGetMethod;
    // Resolved accessories first equipped method
    private static Method accessoriesFirstEquippedMethod;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the controller runtime observer
    private ControllerRuntimeObserver() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Check if this has bound goggles
    static boolean hasBoundGoggles(Player player, BlockPos pos, UUID subLevelId) {
        if (player == null || pos == null) {
            return false;
        }
        ItemStack goggles = wornGoggles(player);
        if (goggles.isEmpty()) {
            return false;
        }
        CompoundTag binding = goggles.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .getUnsafe()
                .getCompound(AnalogueContraptionControllerMenu.GOGGLES_BIND_ROOT);
        if (binding.isEmpty()) {
            return false;
        }
        BlockPos boundPos = new BlockPos(
                binding.getInt(AnalogueContraptionControllerMenu.GOGGLES_BIND_X),
                binding.getInt(AnalogueContraptionControllerMenu.GOGGLES_BIND_Y),
                binding.getInt(AnalogueContraptionControllerMenu.GOGGLES_BIND_Z));
        UUID boundSubLevelId = binding.hasUUID(AnalogueContraptionControllerMenu.GOGGLES_BIND_SUBLEVEL)
                ? binding.getUUID(AnalogueContraptionControllerMenu.GOGGLES_BIND_SUBLEVEL)
                : null;
        return pos.equals(boundPos) && Objects.equals(subLevelId, boundSubLevelId);
    }

    // Check if this has paired goggles
    static boolean hasPairedGoggles(Player player, Set<UUID> pairIds) {
        if (player == null || pairIds == null || pairIds.isEmpty()) {
            return false;
        }
        UUID pairId = gogglesPairId(player);
        return pairId != null && pairIds.contains(pairId);
    }

    // Get the goggles pair id
    static UUID gogglesPairId(Player player) {
        return player == null ? null : gogglesPairId(wornGoggles(player));
    }

    // Find the goggles wearer
    static LivingEntity findGogglesWearer(MinecraftServer server, UUID pairId) {
        if (server == null || pairId == null) {
            return null;
        }
        synchronized (SERVER_CACHES) {
            ServerObserverCache cache = SERVER_CACHES.computeIfAbsent(
                    server, ignored -> new ServerObserverCache());
            cache.refreshWearers(server, serverTick(server));
            LivingEntity wearer = cache.wearersByPair.get(pairId);
            return wearer == null || wearer.isRemoved() ? null : wearer;
        }
    }

    // Get the graph observers
    static List<GraphObserver> graphObservers(
            MinecraftServer server,
            AdvancedContraptionControllerBlockEntity controller,
            BlockPos pos,
            UUID subLevelId,
            boolean portable,
            Set<UUID> pairIds,
            boolean liveMenus
    ) {
        if (server == null || controller == null || pos == null) {
            return List.of();
        }
        synchronized (SERVER_CACHES) {
            long tick = serverTick(server);
            ServerObserverCache cache = SERVER_CACHES.computeIfAbsent(
                    server, ignored -> new ServerObserverCache());
            cache.refreshEquipment(server, tick);
            List<ServerPlayer> menuPlayers = liveMenus
                    ? liveMenuPlayers(server, controller)
                    : cache.menuPlayers(server, controller, tick);
            LinkedHashMap<UUID, GraphObserver> observers = new LinkedHashMap<>();
            for (ServerPlayer player : menuPlayers) {
                observers.put(player.getUUID(), new GraphObserver(
                        player, player.containerMenu.containerId, null));
            }
            BoundTarget target = new BoundTarget(subLevelId, pos);
            for (EquippedPlayer equipped : cache.boundPlayers.getOrDefault(target, List.of())) {
                observers.putIfAbsent(equipped.player().getUUID(),
                        new GraphObserver(equipped.player(), -1, equipped.pairId()));
            }
            if (portable && pairIds != null) {
                for (UUID pairId : pairIds) {
                    for (EquippedPlayer equipped :
                            cache.pairedPlayers.getOrDefault(pairId, List.of())) {
                        observers.putIfAbsent(equipped.player().getUUID(),
                                new GraphObserver(equipped.player(), -1, pairId));
                    }
                }
            }
            return List.copyOf(observers.values());
        }
    }

    // Clear the controller runtime observer
    static void clear(MinecraftServer server) {
        synchronized (SERVER_CACHES) {
            SERVER_CACHES.remove(server);
        }
    }

    // Get the goggles pair id
    static UUID gogglesPairId(ItemStack goggles) {
        if (goggles == null || goggles.isEmpty()) {
            return null;
        }
        CompoundTag binding = goggles.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .getUnsafe()
                .getCompound(AnalogueContraptionControllerMenu.GOGGLES_BIND_ROOT);
        return binding.hasUUID(AnalogueContraptionControllerMenu.GOGGLES_BIND_PAIR_ID)
                ? binding.getUUID(AnalogueContraptionControllerMenu.GOGGLES_BIND_PAIR_ID) : null;
    }

    // Get the worn goggles
    private static ItemStack wornGoggles(LivingEntity wearer) {
        ItemStack head = wearer.getItemBySlot(EquipmentSlot.HEAD);
        if (PhysicsGogglesItem.isFunctionalGoggles(head)) {
            return head;
        }
        if (!(wearer instanceof Player player)) {
            return ItemStack.EMPTY;
        }
        ItemStack curios = curiosGoggles(player);
        return curios.isEmpty() ? accessoriesGoggles(player) : curios;
    }

    // Update the server
    private static long serverTick(MinecraftServer server) {
        return server.overworld().getGameTime();
    }

    // Get the live menu players
    private static List<ServerPlayer> liveMenuPlayers(
            MinecraftServer server, AdvancedContraptionControllerBlockEntity controller) {
        List<ServerPlayer> res = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.containerMenu instanceof AdvancedContraptionControllerMenu menu
                    && menu.getMenuConfigTargetBlockEntity() == controller) {
                res.add(player);
            }
        }
        return res;
    }

    // Get the curios goggles
    private static ItemStack curiosGoggles(Player player) {
        initCurios();
        if (curiosInventoryMethod == null) {
            return ItemStack.EMPTY;
        }
        try {
            Optional<?> inventory = (Optional<?>) curiosInventoryMethod.invoke(null, player);
            if (inventory.isEmpty()) {
                return ItemStack.EMPTY;
            }
            Object inventoryValue = inventory.get();
            if (curiosFindMethod == null || curiosInventoryClass != inventoryValue.getClass()) {
                curiosInventoryClass = inventoryValue.getClass();
                curiosFindMethod = curiosInventoryClass.getMethod("findFirstCurio", Item.class);
            }
            ItemStack res = findCurio(inventoryValue, CTItems.PHYSICS_GOGGLES == null
                    ? null : CTItems.PHYSICS_GOGGLES.get());
            return res;
        } catch (ReflectiveOperationException ignored) {
            return ItemStack.EMPTY;
        }
    }

    // Get the accessories goggles
    private static ItemStack accessoriesGoggles(Player player) {
        initAccessories();
        if (accessoriesGetMethod == null || accessoriesFirstEquippedMethod == null) {
            return ItemStack.EMPTY;
        }
        try {
            Object capability = accessoriesGetMethod.invoke(null, player);
            if (capability == null) {
                return ItemStack.EMPTY;
            }
            ItemStack res = findAccessory(capability, CTItems.PHYSICS_GOGGLES == null
                    ? null : CTItems.PHYSICS_GOGGLES.get());
            return res;
        } catch (ReflectiveOperationException ignored) {
            return ItemStack.EMPTY;
        }
    }

    // Find the curio
    private static ItemStack findCurio(Object inventory, Item item) throws ReflectiveOperationException {
        if (item == null) return ItemStack.EMPTY;
        Optional<?> res = (Optional<?>) curiosFindMethod.invoke(inventory, item);
        return res.isEmpty() ? ItemStack.EMPTY : stackFromSlotResult(res.get());
    }

    // Find the accessory
    private static ItemStack findAccessory(Object capability, Item item) throws ReflectiveOperationException {
        if (item == null) return ItemStack.EMPTY;
        Object res = accessoriesFirstEquippedMethod.invoke(capability, item);
        return res == null ? ItemStack.EMPTY : stackFromSlotResult(res);
    }

    // Initialize the curios
    private static void initCurios() {
        if (curiosInitialized) {
            return;
        }
        curiosInitialized = true;
        if (!ModList.get().isLoaded("curios")) {
            return;
        }
        try {
            Class<?> curiosApiClass = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            curiosInventoryMethod = curiosApiClass.getMethod(
                    "getCuriosInventory", net.minecraft.world.entity.LivingEntity.class);
        } catch (ReflectiveOperationException ignored) {
            curiosInventoryMethod = null;
        }
    }

    // Initialize the accessories
    private static void initAccessories() {
        if (accessoriesInitialized) {
            return;
        }
        accessoriesInitialized = true;
        if (!ModList.get().isLoaded("accessories")) {
            return;
        }
        try {
            Class<?> capabilityClass = Class.forName("io.wispforest.accessories.api.AccessoriesCapability");
            accessoriesGetMethod = capabilityClass.getMethod(
                    "get", net.minecraft.world.entity.LivingEntity.class);
            accessoriesFirstEquippedMethod = capabilityClass.getMethod("getFirstEquipped", Item.class);
        } catch (ReflectiveOperationException ignored) {
            accessoriesGetMethod = null;
            accessoriesFirstEquippedMethod = null;
        }
    }

    // Get the stack from slot result
    private static ItemStack stackFromSlotResult(Object res) throws ReflectiveOperationException {
        Method stackMethod;
        try {
            stackMethod = res.getClass().getMethod("stack");
        } catch (NoSuchMethodException err) {
            stackMethod = res.getClass().getMethod("getStack");
        }
        Object stack = stackMethod.invoke(res);
        return stack instanceof ItemStack itemStack ? itemStack : ItemStack.EMPTY;
    }

    // Store the graph observer
    record GraphObserver(ServerPlayer player, int containerId, UUID pairId) {
    }

    // Store the bound target
    private record BoundTarget(UUID subLevelId, BlockPos position) {
        // Initialize the bound target
        private BoundTarget {
            position = position.immutable();
        }
    }

    // Store the equipped player
    private record EquippedPlayer(ServerPlayer player, UUID pairId) {
    }

    // Handle the server observer cache
    private static final class ServerObserverCache {
        // Tracked bound players
        private final Map<BoundTarget, List<EquippedPlayer>> boundPlayers =
                new LinkedHashMap<>();
        // Tracked paired players
        private final Map<UUID, List<EquippedPlayer>> pairedPlayers = new LinkedHashMap<>();
        // Tracked menu players
        private final IdentityHashMap<AdvancedContraptionControllerBlockEntity,
                List<ServerPlayer>> menuPlayers = new IdentityHashMap<>();
        // Wearers indexed by pair
        private final Map<UUID, LivingEntity> wearersByPair = new LinkedHashMap<>();
        // Equipment tick
        private long equipmentTick = Long.MIN_VALUE;
        // Menu tick
        private long menuTick = Long.MIN_VALUE;
        // Wearer tick
        private long wearerTick = Long.MIN_VALUE;

        // Refresh the equipment
        private void refreshEquipment(MinecraftServer server, long tick) {
            if (equipmentTick != Long.MIN_VALUE && tick >= equipmentTick
                    && tick - equipmentTick < EQUIPMENT_SNAPSHOT_TICKS) {
                return;
            }
            boundPlayers.clear();
            pairedPlayers.clear();
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                ItemStack goggles = wornGoggles(player);
                if (goggles.isEmpty()) {
                    continue;
                }
                CompoundTag binding = goggles.getOrDefault(
                                DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                        .getUnsafe().getCompound(
                                AnalogueContraptionControllerMenu.GOGGLES_BIND_ROOT);
                if (binding.isEmpty()) {
                    continue;
                }
                UUID pairId = binding.hasUUID(
                        AnalogueContraptionControllerMenu.GOGGLES_BIND_PAIR_ID)
                        ? binding.getUUID(AnalogueContraptionControllerMenu.GOGGLES_BIND_PAIR_ID)
                        : null;
                EquippedPlayer equipped = new EquippedPlayer(player, pairId);
                UUID boundSubLevelId = binding.hasUUID(
                        AnalogueContraptionControllerMenu.GOGGLES_BIND_SUBLEVEL)
                        ? binding.getUUID(AnalogueContraptionControllerMenu.GOGGLES_BIND_SUBLEVEL)
                        : null;
                BoundTarget target = new BoundTarget(boundSubLevelId, new BlockPos(
                        binding.getInt(AnalogueContraptionControllerMenu.GOGGLES_BIND_X),
                        binding.getInt(AnalogueContraptionControllerMenu.GOGGLES_BIND_Y),
                        binding.getInt(AnalogueContraptionControllerMenu.GOGGLES_BIND_Z)));
                boundPlayers.computeIfAbsent(target, ignored -> new ArrayList<>()).add(equipped);
                if (pairId != null) {
                    pairedPlayers.computeIfAbsent(pairId, ignored -> new ArrayList<>()).add(equipped);
                }
            }
            equipmentTick = tick;
        }

        // Get the menu players
        private List<ServerPlayer> menuPlayers(
                MinecraftServer server,
                AdvancedContraptionControllerBlockEntity controller,
                long tick) {
            if (menuTick != tick) {
                menuPlayers.clear();
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    if (player.containerMenu instanceof AdvancedContraptionControllerMenu menu) {
                        AdvancedContraptionControllerBlockEntity target =
                                menu.getMenuConfigTargetBlockEntity();
                        if (target != null) {
                            menuPlayers.computeIfAbsent(target,
                                    ignored -> new ArrayList<>()).add(player);
                        }
                    }
                }
                menuTick = tick;
            }
            return menuPlayers.getOrDefault(controller, List.of());
        }

        // Refresh the wearers
        private void refreshWearers(MinecraftServer server, long tick) {
            if (wearerTick != Long.MIN_VALUE && tick >= wearerTick
                    && tick - wearerTick < WEARER_SNAPSHOT_TICKS) {
                return;
            }
            wearersByPair.clear();
            for (ServerLevel level : server.getAllLevels()) {
                for (net.minecraft.world.entity.Entity entity : level.getAllEntities()) {
                    if (!(entity instanceof LivingEntity living)
                            || (!(living instanceof Player) && !(living instanceof ArmorStand))
                            || living.isRemoved()) {
                        continue;
                    }
                    UUID pairId = gogglesPairId(wornGoggles(living));
                    if (pairId != null) {
                        wearersByPair.putIfAbsent(pairId, living);
                    }
                }
            }
            wearerTick = tick;
        }
    }
}
