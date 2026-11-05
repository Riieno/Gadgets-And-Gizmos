package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.neoforge.network.CTPayloadTransfers;
import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphLiveValue;
import com.rieno.gadgetsandgizmos.lib.util.ValueDelta;
import com.rieno.gadgetsandgizmos.lib.graph.GraphRuntimeJournal;
import com.rieno.gadgetsandgizmos.lib.graph.GraphRuntimeProjection;
import com.rieno.gadgetsandgizmos.lib.graph.GraphRuntimeNbt;
import com.rieno.gadgetsandgizmos.lib.nbt.CompoundTagDelta;
import com.rieno.gadgetsandgizmos.lib.menuconfig.MenuConfigTarget;
import com.rieno.gadgetsandgizmos.content.advanced.GraphRuntime;
import com.rieno.gadgetsandgizmos.neoforge.network.AdvancedControllerRuntimeEventsPayload;
import net.minecraft.nbt.CompoundTag;
import com.rieno.gadgetsandgizmos.neoforge.network.AdvancedControllerRuntimeDeltaPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.AdvancedControllerRuntimePayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// Publish HUD port changes independently for each connected observer
final class AdvancedHudRuntimeSync{
/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        DEFAULTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    // Recover a client baseline periodically after a local controller cache is recreated
    private static final int FULL_UPDATE_INTERVAL = 100;
    // Retain delivered values only while their player observes this controller
    private final Map<UUID, Baseline> baselines = new HashMap<>();
    private final Map<UUID, EventReader> readers = new HashMap<>();

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                       FUNCTIONS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    static boolean supportsEvents(ServerPlayer player){
        return NetworkRegistry.hasChannel(player.connection, AdvancedControllerRuntimeEventsPayload.TYPE.id());
    }

    // Shared port values stay in the runtime; only personal overrides are retained for each reader
    boolean sendEvents(AdvancedContraptionControllerBlockEntity controller, ServerPlayer player, int containerId,
            Map<UUID, String> pairs, Map<String, AdvancedGraphDocument.Value> inputs,
            Map<String, AdvancedGraphDocument.Value> outputs, Map<String, AdvancedGraphDocument.Value> variables,
            Map<UUID, String> items){
        if(!supportsEvents(player)) return false;
        EventReader reader = readers.get(player.getUUID());
        GraphRuntime runtime = controller.graphRuntimeForDraftSession();
        if(reader == null || reader.connection != player.connection || reader.containerId != containerId){
            forget(player.getUUID());
            reader = new EventReader(runtime, player.connection, containerId);
            readers.put(player.getUUID(), reader);
        }
        CompoundTag metadata = new CompoundTag();
        CompoundTag pairData = com.rieno.gadgetsandgizmos.lib.nbt.UuidStringTags.write(pairs);
        CompoundTag itemData = com.rieno.gadgetsandgizmos.lib.nbt.UuidStringTags.write(items);
        metadata.put("Pairs", pairData);
        metadata.put("Items", itemData);
        metadata.put("HudLayout", runtime.hudPresentationLayout());
        boolean changed = !metadata.equals(reader.metadata);
        Map<String, AdvancedGraphDocument.Value> personalVariables = new LinkedHashMap<>();
        variables.forEach((key, val) -> {
            if(!java.util.Objects.equals(val, runtime.runtimeValue(GraphRuntimeJournal.Channel.VARIABLE, key))) personalVariables.put(key, val);
        });
        var source = runtime.runtimeChanges(reader.projection.sourceStream(), reader.projection.sourceSequence());
        long base = reader.projection.sequence();
        var update = reader.projection.project(source, Map.of(GraphRuntimeJournal.Channel.INPUT, inputs,
                GraphRuntimeJournal.Channel.OUTPUT, outputs, GraphRuntimeJournal.Channel.VARIABLE, personalVariables),
                runtime::runtimeValue, changed);
        if(!update.reset() && update.sequence() == base) return true;
        CompoundTag patch = update.reset() ? metadata : CompoundTagDelta.between(reader.metadata, metadata);
        CTPayloadTransfers.CHANNEL.sendToPlayer(player, new AdvancedControllerRuntimeEventsPayload(
                MenuConfigTarget.of(controller.getBlockPos(), SimulatedHelper.getContainingSubLevelId(controller)), containerId,
                update.reset() ? 0 : base, pairData, GraphRuntimeNbt.write(update, AdvancedGraphDocument.Value::toTag), patch));
        reader.metadata = metadata;
        return true;
    }

    // Send changed HUD values while retaining the original full payload for older clients
    void send(AdvancedContraptionControllerBlockEntity controller, ServerPlayer player,
              Map<UUID, String> pairs, Map<String, AdvancedGraphDocument.Value> inputs,
              Map<String, AdvancedGraphDocument.Value> outputs, Map<String, Long> pulses,
              Map<String, AdvancedGraphDocument.Value> variables, Map<UUID, String> items){
        send(controller, player, -1, pairs, inputs, outputs, pulses, variables, items);
    }

    // Use the same ordered value transport for editor and remote diagnostic observers
    void send(AdvancedContraptionControllerBlockEntity controller, ServerPlayer player, int containerId,
              Map<UUID, String> pairs, Map<String, AdvancedGraphDocument.Value> inputs,
              Map<String, AdvancedGraphDocument.Value> outputs, Map<String, Long> pulses,
              Map<String, AdvancedGraphDocument.Value> variables, Map<UUID, String> items){
        if(!NetworkRegistry.hasChannel(player.connection, AdvancedControllerRuntimeDeltaPayload.TYPE.id())){
            baselines.remove(player.getUUID());
            CTPayloadTransfers.CHANNEL.sendToPlayer(player, payload(controller, containerId, pairs, inputs, outputs, pulses, variables, items));
            return;
        }
        // ------------------------------------CHECK THE BASELINE------------------------------------
        long now = controller.getLevel().getGameTime();
        Baseline prev = baselines.get(player.getUUID());
        var graph = controller.activeGraphView();
        boolean newStream = prev == null || prev.connection() != player.connection
                || prev.graph() != graph || prev.graphRevision() != graph.revision() || prev.containerId() != containerId;
        boolean full = newStream || now < prev.lastFullTick() || now - prev.lastFullTick() >= FULL_UPDATE_INTERVAL;
        ValueDelta<String, AdvancedGraphDocument.Value> inputDelta = ValueDelta.between(full ? null : prev.inputs(), inputs);
        ValueDelta<String, AdvancedGraphDocument.Value> outputDelta = ValueDelta.between(full ? null : prev.outputs(), outputs);
        Metadata metadata = new Metadata(pairs, pulses, variables, items);
        if(!full && inputDelta.isEmpty() && outputDelta.isEmpty() && metadata.equals(prev.metadata())) return;
        // ------------------------------------PUBLISH CHANGED PORTS------------------------------------
        UUID streamId = newStream ? UUID.randomUUID() : prev.streamId();
        long base = newStream ? 0 : prev.sequence();
        var update = payload(controller, containerId, pairs, inputDelta.values(), outputDelta.values(), pulses, variables, items);
        CTPayloadTransfers.CHANNEL.sendToPlayer(player, new AdvancedControllerRuntimeDeltaPayload(update, streamId,
                base, base + 1, full, inputDelta.removed(), outputDelta.removed()));
        baselines.put(player.getUUID(), new Baseline(player.connection, graph, graph.revision(), containerId, streamId,
                base + 1, full ? now : prev.lastFullTick(), Map.copyOf(inputs), Map.copyOf(outputs), metadata));
    }

    // Format only the values included in this full update or delta
    private static AdvancedControllerRuntimePayload payload(AdvancedContraptionControllerBlockEntity controller, int containerId,
            Map<UUID, String> pairs, Map<String, AdvancedGraphDocument.Value> inputs,
            Map<String, AdvancedGraphDocument.Value> outputs, Map<String, Long> pulses,
            Map<String, AdvancedGraphDocument.Value> variables, Map<UUID, String> items){
        return new AdvancedControllerRuntimePayload(controller.getBlockPos(), SimulatedHelper.getContainingSubLevelId(controller),
                containerId, pairs, liveValues(inputs), liveValues(outputs), pulses, variables, items);
    }

    // Convert selected changed ports into their client display representation
    private static Map<String, AdvancedGraphLiveValue> liveValues(Map<String, AdvancedGraphDocument.Value> values){
        Map<String, AdvancedGraphLiveValue> res = new LinkedHashMap<>();
        values.forEach((key, val) -> res.put(key, AdvancedGraphLiveValue.from(val)));
        return res;
    }

    // Require a full baseline after an editor or direct snapshot replaces a player's HUD values
    void forget(UUID playerId){
        baselines.remove(playerId);
        EventReader reader = readers.remove(playerId);
        if(reader != null) reader.runtime.releaseRuntimeObservation();
    }

    // Release players who disconnected, removed their goggles, or changed controllers
    void retain(Set<UUID> playerIds){
        baselines.keySet().retainAll(playerIds);
        for(UUID id : Set.copyOf(readers.keySet())) if(!playerIds.contains(id)) forget(id);
    }

    // Release all baselines after a graph or controller lifecycle change
    void clear(){
        baselines.clear();
        for(UUID id : Set.copyOf(readers.keySet())) forget(id);
    }

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        HELPERS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    private static final class EventReader{
        private final GraphRuntime runtime;
        private final Object connection;
        private final int containerId;
        private final GraphRuntimeProjection<AdvancedGraphDocument.Value> projection = new GraphRuntimeProjection<>();
        private CompoundTag metadata = new CompoundTag();

        private EventReader(GraphRuntime runtime, Object connection, int containerId){
            this.runtime = runtime;
            this.connection = connection;
            this.containerId = containerId;
            runtime.retainRuntimeObservation();
        }
    }

    // Compare complete small metadata groups without treating unchanged ports as updates
    private record Metadata(Map<UUID, String> pairs, Map<String, Long> pulses,
                            Map<String, AdvancedGraphDocument.Value> variables, Map<UUID, String> items){
        // Detach metadata from caller-owned maps retained between publications
        private Metadata{
            pairs = Map.copyOf(pairs);
            pulses = Map.copyOf(pulses);
            variables = Map.copyOf(variables);
            items = Map.copyOf(items);
        }
    }

    // Retain one delivered snapshot and its stream generation for a connected player
    private record Baseline(Object connection, AdvancedGraphDocument graph, int graphRevision, int containerId, UUID streamId,
                            long sequence, long lastFullTick, Map<String, AdvancedGraphDocument.Value> inputs,
                            Map<String, AdvancedGraphDocument.Value> outputs, Metadata metadata){}
}
