package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.simibubi.create.Create;
import com.simibubi.create.content.redstone.link.IRedstoneLinkable;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// Run tablet redstone links on the server and keep their remote sessions short lived
public final class DiagnosticTabletRedstoneLinkRuntime {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final int BUTTON_PULSE_TICKS = 4;
    private static final int DISCOVERY_INTERVAL_TICKS = 20;
    private static final Map<Source, Map<UUID, Transmitter>> ACTORS_BY_SOURCE = new HashMap<>();
    private static final Map<UUID, Source> TABLET_SOURCES = new HashMap<>();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the diagnostic tablet redstone link
    private DiagnosticTabletRedstoneLinkRuntime() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Sync the item
    public static void syncItem(ServerPlayer player, ItemStack tablet,
                                List<DiagnosticTabletData.RedstoneLinkChannel> channels) {
        if (player == null || tablet == null || tablet.isEmpty()) return;
        ItemSource src = itemSource(player, tablet);
        sync(src, player.serverLevel(), player.blockPosition(), channels);
    }

    // Sync the placed
    public static void syncPlaced(ServerPlayer player, DiagnosticTabletBlockEntity tablet,
                                  List<DiagnosticTabletData.RedstoneLinkChannel> channels) {
        if (tablet == null || tablet.getLevel() == null || tablet.getLevel().isClientSide) return;
        MinecraftServer server = tablet.getLevel().getServer();
        if (server == null) return;
        PlacedSource src = placedSource(server, tablet);
        sync(src, tablet.getLevel(), tablet.getBlockPos(), channels);
    }

    // Refresh the placed
    public static void refreshPlaced(DiagnosticTabletBlockEntity tablet) {
        if (tablet == null || tablet.getLevel() == null || tablet.getLevel().isClientSide) return;
        MinecraftServer server = tablet.getLevel().getServer();
        UUID tabletId = tablet.state().tabletId();
        if (server == null || tabletId == null) return;
        DiagnosticTabletDatabase.forServer(server).importLegacy(tabletId, tablet.state());
        syncPlaced(null, tablet, DiagnosticTabletRedstoneLinkApp.channels(server, tabletId));
    }

    // Handle the pulse item
    public static void pulseItem(ServerPlayer player, ItemStack tablet,
                                 List<DiagnosticTabletData.RedstoneLinkChannel> channels,
                                 UUID channelId) {
        if (player == null || tablet == null || tablet.isEmpty()) return;
        pulse(itemSource(player, tablet), player.serverLevel(), player.blockPosition(),
                channels, channelId);
    }

    // Handle the pulse placed
    public static void pulsePlaced(ServerPlayer player, DiagnosticTabletBlockEntity tablet,
                                   List<DiagnosticTabletData.RedstoneLinkChannel> channels,
                                   UUID channelId) {
        if (tablet == null || tablet.getLevel() == null || tablet.getLevel().isClientSide) return;
        MinecraftServer server = tablet.getLevel().getServer();
        if (server == null) return;
        pulse(placedSource(server, tablet), tablet.getLevel(), tablet.getBlockPos(),
                channels, channelId);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the server tick event
    public static void onServerTick(ServerTickEvent.Post evt) {
        MinecraftServer server = evt.getServer();
        boolean discoveryTick = server.getTickCount() % DISCOVERY_INTERVAL_TICKS == 0;
        if (discoveryTick) {
            discoverInventoryTablets(server);
        }

        Iterator<Map.Entry<Source, Map<UUID, Transmitter>>> sourceIterator =
                ACTORS_BY_SOURCE.entrySet().iterator();
        while (sourceIterator.hasNext()) {
            Map.Entry<Source, Map<UUID, Transmitter>> sourceEntry = sourceIterator.next();
            Source src = sourceEntry.getKey();
            Map<UUID, Transmitter> actors = sourceEntry.getValue();
            boolean pulseCompleted = tickPulses(actors);

            SourceLocation fastLocation = src.fastLocation(server);
            if (src instanceof ItemSource && fastLocation == null) {
                removeAll(actors);
                sourceIterator.remove();
                continue;
            }
            if (fastLocation != null) {
                moveAll(actors, fastLocation);
            }

            boolean needsValidation = pulseCompleted
                    || discoveryTick && !(src instanceof ItemSource);
            if (!needsValidation) {
                continue;
            }
            SourceLocation current = src.resolveLocation(server);
            if (current == null) {
                removeAll(actors);
                sourceIterator.remove();
                continue;
            }
            Map<UUID, DiagnosticTabletData.RedstoneLinkChannel> channels = channelsById(
                    DiagnosticTabletRedstoneLinkApp.channels(server, src.tabletId()));
            Iterator<Map.Entry<UUID, Transmitter>> actorIterator = actors.entrySet().iterator();
            while (actorIterator.hasNext()) {
                Map.Entry<UUID, Transmitter> actorEntry = actorIterator.next();
                Transmitter actor = actorEntry.getValue();
                DiagnosticTabletData.RedstoneLinkChannel channel = channels.get(actor.channelId);
                if (channel == null || actor.pulseTicks == 0 && !isPersistent(channel)) {
                    remove(actor);
                    actorIterator.remove();
                    continue;
                }
                update(actor, current.level(), current.position(), channel,
                        actor.pulseTicks > 0 ? channel.strength() : channel.outputStrength());
            }
            if (actors.isEmpty()) {
                sourceIterator.remove();
            }
        }
    }

    // Handle the server stopped event
    public static void onServerStopped(ServerStoppedEvent evt) {
        ACTORS_BY_SOURCE.clear();
        TABLET_SOURCES.clear();
    }

    // Discover the inventory tablets
    private static void discoverInventoryTablets(MinecraftServer server) {
        Set<ItemSource> discovered = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Container inventory = player.getInventory();
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (!(stack.getItem() instanceof DiagnosticTabletItem)) continue;
                DiagnosticTabletData.State state = DiagnosticTabletData.read(stack);
                UUID tabletId = state.tabletId();
                if (tabletId == null) {
                    tabletId = UUID.randomUUID();
                    state = state.withTabletId(tabletId);
                    DiagnosticTabletData.write(stack, state);
                }
                ItemSource src = new ItemSource(player.getUUID(), tabletId);
                discovered.add(src);
                rememberSource(tabletId, src);
                DiagnosticTabletDatabase.forServer(server).importLegacy(tabletId, state);
                sync(src, player.serverLevel(), player.blockPosition(),
                        DiagnosticTabletRedstoneLinkApp.channels(server, tabletId));
            }
        }
        Iterator<Map.Entry<UUID, Source>> iterator = TABLET_SOURCES.entrySet().iterator();
        while (iterator.hasNext()) {
            Source src = iterator.next().getValue();
            if (src instanceof ItemSource itemSource && !discovered.contains(itemSource)) {
                removeSource(src);
                iterator.remove();
            }
        }
    }

    // Get the item source
    private static ItemSource itemSource(ServerPlayer player, ItemStack tablet) {
        DiagnosticTabletData.State state = DiagnosticTabletData.read(tablet);
        UUID tabletId = state.tabletId();
        if (tabletId == null) {
            tabletId = UUID.randomUUID();
            state = state.withTabletId(tabletId);
            DiagnosticTabletData.write(tablet, state);
        }
        ItemSource src = new ItemSource(player.getUUID(), tabletId);
        rememberSource(tabletId, src);
        return src;
    }

    // Get the placed source
    private static PlacedSource placedSource(MinecraftServer server,
                                             DiagnosticTabletBlockEntity tablet) {
        DiagnosticTabletData.State state = tablet.state();
        UUID tabletId = state.tabletId();
        UUID subLevelId = SimulatedHelper.getContainingSubLevelId(tablet);
        ResourceKey<Level> dimension = parentDimension(server, tablet, subLevelId);
        if (tabletId == null) {
            tabletId = UUID.randomUUID();
            state = state.withTabletId(tabletId);
            tablet.setState(state);
        }
        PlacedSource src = new PlacedSource(dimension, subLevelId,
                tablet.getBlockPos(), tabletId);
        rememberSource(tabletId, src);
        return src;
    }

    // Remember the source
    private static void rememberSource(UUID tabletId, Source src) {
        Source prev = TABLET_SOURCES.put(tabletId, src);
        if (prev != null && !prev.equals(src)) {
            removeSource(prev);
        }
    }

    // Get the parent dimension
    private static ResourceKey<Level> parentDimension(MinecraftServer server,
                                                       DiagnosticTabletBlockEntity tablet,
                                                       UUID subLevelId) {
        if (subLevelId != null) {
            for (ServerLevel candidate : server.getAllLevels()) {
                if (SimulatedHelper.findLoadedBlockEntityExact(candidate, subLevelId,
                        tablet.getBlockPos()) == tablet) {
                    return candidate.dimension();
                }
            }
        }
        return tablet.getLevel().dimension();
    }

    // Sync the diagnostic tablet redstone link
    private static void sync(Source src, LevelAccessor level, BlockPos pos,
                             List<DiagnosticTabletData.RedstoneLinkChannel> channels) {
        Map<UUID, DiagnosticTabletData.RedstoneLinkChannel> desired = new HashMap<>();
        for (DiagnosticTabletData.RedstoneLinkChannel channel : channels) {
            if (channel.controlMode() != DiagnosticTabletData.RedstoneLinkControlMode.BUTTON
                    && channel.outputStrength() > 0 && hasValidFrequency(channel)) {
                desired.put(channel.id(), channel);
            }
        }
        Map<UUID, Transmitter> actors = ACTORS_BY_SOURCE.get(src);
        if (actors != null) {
            Iterator<Map.Entry<UUID, Transmitter>> iterator = actors.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<UUID, Transmitter> entry = iterator.next();
                if (entry.getValue().pulseTicks > 0) {
                    desired.remove(entry.getKey());
                    continue;
                }
                DiagnosticTabletData.RedstoneLinkChannel channel = desired.remove(entry.getKey());
                if (channel == null) {
                    remove(entry.getValue());
                    iterator.remove();
                } else {
                    update(entry.getValue(), level, pos, channel, channel.outputStrength());
                }
            }
            if (actors.isEmpty()) {
                ACTORS_BY_SOURCE.remove(src);
            }
        }
        desired.values().forEach(channel -> add(src, level, pos, channel,
                channel.outputStrength(), 0));
    }

    // Check if this is persistent
    private static boolean isPersistent(DiagnosticTabletData.RedstoneLinkChannel channel) {
        return channel != null
                && channel.controlMode()
                        != DiagnosticTabletData.RedstoneLinkControlMode.BUTTON
                && channel.outputStrength() > 0
                && hasValidFrequency(channel);
    }

    // Pulse the redstone link
    private static void pulse(Source src, LevelAccessor level, BlockPos pos,
                              List<DiagnosticTabletData.RedstoneLinkChannel> channels, UUID channelId) {
        DiagnosticTabletData.RedstoneLinkChannel channel = channels.stream()
                .filter(candidate -> candidate.id().equals(channelId))
                .filter(candidate -> candidate.controlMode()
                        == DiagnosticTabletData.RedstoneLinkControlMode.BUTTON)
                .findFirst().orElse(null);
        if (channel == null || !hasValidFrequency(channel)) return;
        Map<UUID, Transmitter> actors = ACTORS_BY_SOURCE.get(src);
        Transmitter actor = actors == null ? null : actors.get(channel.id());
        if (actor == null) {
            add(src, level, pos, channel, channel.strength(), BUTTON_PULSE_TICKS);
        } else {
            actor.pulseTicks = BUTTON_PULSE_TICKS;
            update(actor, level, pos, channel, channel.strength());
        }
    }

    // Add the diagnostic tablet redstone link
    private static void add(Source src, LevelAccessor level, BlockPos pos,
                            DiagnosticTabletData.RedstoneLinkChannel channel,
                            int strength, int pulseTicks) {
        Couple<RedstoneLinkNetworkHandler.Frequency> key = networkKey(channel);
        Transmitter actor = new Transmitter(src, channel.id(), level, pos,
                key, strength, pulseTicks);
        ACTORS_BY_SOURCE.computeIfAbsent(src, ignored -> new HashMap<>())
                .put(channel.id(), actor);
        Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(level, actor);
    }

    // Update the pulses
    private static boolean tickPulses(Map<UUID, Transmitter> actors) {
        boolean completed = false;
        for (Transmitter actor : actors.values()) {
            if (actor.pulseTicks > 0 && --actor.pulseTicks == 0) {
                completed = true;
            }
        }
        return completed;
    }

    // Move every redstone link transmitter
    private static void moveAll(Map<UUID, Transmitter> actors, SourceLocation location) {
        for (Transmitter actor : actors.values()) {
            move(actor, location.level(), location.position());
        }
    }

    // Move the diagnostic tablet redstone link
    private static void move(Transmitter actor, LevelAccessor level, BlockPos pos) {
        if (actor.level != level) {
            Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(actor.level, actor);
            actor.level = level;
            actor.position = pos.immutable();
            Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(level, actor);
            return;
        }
        if (actor.position.equals(pos)) {
            return;
        }
        actor.position = pos.immutable();
        Create.REDSTONE_LINK_NETWORK_HANDLER.updateNetworkOf(level, actor);
    }

    // Remove every redstone link transmitter
    private static void removeAll(Map<UUID, Transmitter> actors) {
        actors.values().forEach(DiagnosticTabletRedstoneLinkRuntime::remove);
        actors.clear();
    }

    // Remove the source
    private static void removeSource(Source src) {
        Map<UUID, Transmitter> actors = ACTORS_BY_SOURCE.remove(src);
        if (actors != null) {
            removeAll(actors);
        }
    }

    // Get the channels by id
    private static Map<UUID, DiagnosticTabletData.RedstoneLinkChannel> channelsById(
            List<DiagnosticTabletData.RedstoneLinkChannel> channels) {
        Map<UUID, DiagnosticTabletData.RedstoneLinkChannel> indexed = new HashMap<>(channels.size());
        for (DiagnosticTabletData.RedstoneLinkChannel channel : channels) {
            indexed.put(channel.id(), channel);
        }
        return indexed;
    }

    // Update the diagnostic tablet redstone link
    private static void update(Transmitter actor, LevelAccessor level, BlockPos pos,
                               DiagnosticTabletData.RedstoneLinkChannel channel, int strength) {
        Couple<RedstoneLinkNetworkHandler.Frequency> key = networkKey(channel);
        boolean networkChanged = actor.level != level || !actor.networkKey.equals(key);
        if (networkChanged) {
            Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(actor.level, actor);
            actor.level = level;
            actor.networkKey = key;
            actor.position = pos.immutable();
            actor.strength = strength;
            Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(level, actor);
            return;
        }
        boolean changed = actor.strength != strength || !actor.position.equals(pos);
        actor.position = pos.immutable();
        actor.strength = strength;
        if (changed) Create.REDSTONE_LINK_NETWORK_HANDLER.updateNetworkOf(level, actor);
    }

    // Remove the diagnostic tablet redstone link
    private static void remove(Transmitter actor) {
        actor.strength = 0;
        Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(actor.level, actor);
    }

    // Get the network key
    private static Couple<RedstoneLinkNetworkHandler.Frequency> networkKey(
            DiagnosticTabletData.RedstoneLinkChannel channel) {
        return Couple.create(RedstoneLinkNetworkHandler.Frequency.of(
                        DiagnosticTabletRedstoneLinkApp.frequencyStack(
                                channel.firstItem(), channel.firstColor())),
                RedstoneLinkNetworkHandler.Frequency.of(
                        DiagnosticTabletRedstoneLinkApp.frequencyStack(
                                channel.secondItem(), channel.secondColor())));
    }

    // Check if this has valid frequency
    private static boolean hasValidFrequency(
            DiagnosticTabletData.RedstoneLinkChannel channel) {
        return !DiagnosticTabletRedstoneLinkApp.frequencyStack(
                channel.firstItem(), channel.firstColor()).isEmpty()
                && !DiagnosticTabletRedstoneLinkApp.frequencyStack(
                channel.secondItem(), channel.secondColor()).isEmpty();
    }

    // Expose the source
    private sealed interface Source permits ItemSource, PlacedSource {
        // Get the tablet id
        UUID tabletId();

        // Resolve the location
        SourceLocation resolveLocation(MinecraftServer server);

        // Get the fast location
        default SourceLocation fastLocation(MinecraftServer server) {
            return null;
        }
    }

    // Store the item source
    private record ItemSource(UUID owner, UUID tabletId) implements Source {
        // Resolve the location
        @Override
        public SourceLocation resolveLocation(MinecraftServer server) {
            ItemStack tablet = findTablet(server);
            ServerPlayer player = server.getPlayerList().getPlayer(owner);
            if (tablet.isEmpty() || player == null) return null;
            return new SourceLocation(player.serverLevel(), player.blockPosition());
        }

        // Get the fast location
        @Override
        public SourceLocation fastLocation(MinecraftServer server) {
            ServerPlayer player = server.getPlayerList().getPlayer(owner);
            return player == null ? null
                    : new SourceLocation(player.serverLevel(), player.blockPosition());
        }

        // Find the tablet
        private ItemStack findTablet(MinecraftServer server) {
            ServerPlayer player = server.getPlayerList().getPlayer(owner);
            if (player == null) return ItemStack.EMPTY;
            Container inventory = player.getInventory();
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (stack.getItem() instanceof DiagnosticTabletItem
                        && tabletId.equals(DiagnosticTabletData.read(stack).tabletId())) {
                    return stack;
                }
            }
            return ItemStack.EMPTY;
        }
    }

    // Store the placed source
    private record PlacedSource(ResourceKey<Level> dimension, UUID subLevelId,
                                BlockPos tabletPos, UUID tabletId) implements Source {
        // Initialize the placed source
        private PlacedSource {
            tabletPos = tabletPos.immutable();
        }

        // Resolve the location
        @Override
        public SourceLocation resolveLocation(MinecraftServer server) {
            DiagnosticTabletBlockEntity tablet = tablet(server);
            if (tablet == null || tablet.getLevel() == null) return null;
            return new SourceLocation(tablet.getLevel(), tabletPos);
        }

        // Get the tablet
        private DiagnosticTabletBlockEntity tablet(MinecraftServer server) {
            ServerLevel parent = server.getLevel(dimension);
            if (parent == null) return null;
            return SimulatedHelper.findLoadedBlockEntityExact(parent, subLevelId, tabletPos)
                    instanceof DiagnosticTabletBlockEntity tablet
                    && tabletId.equals(tablet.state().tabletId()) ? tablet : null;
        }
    }

    // Store the source location
    private record SourceLocation(LevelAccessor level, BlockPos position) {
    }

    // Track the redstone link transmitter
    private static final class Transmitter implements IRedstoneLinkable {
        // Transmitter source
        private final Source source;
        // Channel id
        private final UUID channelId;
        // Current level
        private LevelAccessor level;
        // Current transmitter position
        private BlockPos position;
        // Current network key
        private Couple<RedstoneLinkNetworkHandler.Frequency> networkKey;
        // Current strength
        private int strength;
        // Pulse tick count
        private int pulseTicks;

        // Initialize the transmitter
        private Transmitter(Source src, UUID channelId, LevelAccessor level,
                            BlockPos pos,
                            Couple<RedstoneLinkNetworkHandler.Frequency> networkKey,
                            int strength, int pulseTicks) {
            this.source = src;
            this.channelId = channelId;
            this.level = level;
            this.position = pos.immutable();
            this.networkKey = networkKey;
            this.strength = strength;
            this.pulseTicks = pulseTicks;
        }

        // Get the transmitted strength
        @Override
        public int getTransmittedStrength() {
            return strength;
        }

        // Set the received strength
        @Override
        public void setReceivedStrength(int strength) {
        }

        // Check if this is listening
        @Override
        public boolean isListening() {
            return false;
        }

        // Check if this is alive
        @Override
        public boolean isAlive() {
            return true;
        }

        // Get the network key
        @Override
        public Couple<RedstoneLinkNetworkHandler.Frequency> getNetworkKey() {
            return networkKey;
        }

        // Get the location
        @Override
        public BlockPos getLocation() {
            return position;
        }
    }
}
