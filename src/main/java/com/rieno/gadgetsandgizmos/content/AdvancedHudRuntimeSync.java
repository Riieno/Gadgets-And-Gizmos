package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphLiveValue;
import com.rieno.gadgetsandgizmos.lib.util.ValueDelta;
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
    // Recover a client baseline periodically after a local controller cache is recreated
    private static final int FULL_UPDATE_INTERVAL = 100;
    // Retain delivered values only while their player observes this controller
    private final Map<UUID, Baseline> baselines = new HashMap<>();

    // Send changed HUD values while retaining the original full payload for older clients
    void send(AdvancedContraptionControllerBlockEntity controller, ServerPlayer player,
              Map<UUID, String> pairs, Map<String, AdvancedGraphDocument.Value> inputs,
              Map<String, AdvancedGraphDocument.Value> outputs, Map<String, Long> pulses,
              Map<String, AdvancedGraphDocument.Value> variables, Map<UUID, String> items){
        if(!NetworkRegistry.hasChannel(player.connection, AdvancedControllerRuntimeDeltaPayload.TYPE.id())){
            baselines.remove(player.getUUID());
            PacketDistributor.sendToPlayer(player, payload(controller, pairs, inputs, outputs, pulses, variables, items));
            return;
        }
        // ------------------------------------CHECK THE BASELINE------------------------------------
        long now = controller.getLevel().getGameTime();
        Baseline prev = baselines.get(player.getUUID());
        var graph = controller.activeGraphView();
        boolean newStream = prev == null || prev.connection() != player.connection
                || prev.graph() != graph || prev.graphRevision() != graph.revision();
        boolean full = newStream || now < prev.lastFullTick() || now - prev.lastFullTick() >= FULL_UPDATE_INTERVAL;
        ValueDelta<String, AdvancedGraphDocument.Value> inputDelta = ValueDelta.between(full ? null : prev.inputs(), inputs);
        ValueDelta<String, AdvancedGraphDocument.Value> outputDelta = ValueDelta.between(full ? null : prev.outputs(), outputs);
        Metadata metadata = new Metadata(pairs, pulses, variables, items);
        if(!full && inputDelta.isEmpty() && outputDelta.isEmpty() && metadata.equals(prev.metadata())) return;
        // ------------------------------------PUBLISH CHANGED PORTS------------------------------------
        UUID streamId = newStream ? UUID.randomUUID() : prev.streamId();
        long base = newStream ? 0 : prev.sequence();
        var update = payload(controller, pairs, inputDelta.values(), outputDelta.values(), pulses, variables, items);
        PacketDistributor.sendToPlayer(player, new AdvancedControllerRuntimeDeltaPayload(update, streamId,
                base, base + 1, full, inputDelta.removed(), outputDelta.removed()));
        baselines.put(player.getUUID(), new Baseline(player.connection, graph, graph.revision(), streamId,
                base + 1, full ? now : prev.lastFullTick(), Map.copyOf(inputs), Map.copyOf(outputs), metadata));
    }

    // Format only the values included in this full update or delta
    private static AdvancedControllerRuntimePayload payload(AdvancedContraptionControllerBlockEntity controller,
            Map<UUID, String> pairs, Map<String, AdvancedGraphDocument.Value> inputs,
            Map<String, AdvancedGraphDocument.Value> outputs, Map<String, Long> pulses,
            Map<String, AdvancedGraphDocument.Value> variables, Map<UUID, String> items){
        return new AdvancedControllerRuntimePayload(controller.getBlockPos(), SimulatedHelper.getContainingSubLevelId(controller),
                -1, pairs, liveValues(inputs), liveValues(outputs), pulses, variables, items);
    }

    // Convert selected changed ports into their client display representation
    private static Map<String, AdvancedGraphLiveValue> liveValues(Map<String, AdvancedGraphDocument.Value> values){
        Map<String, AdvancedGraphLiveValue> res = new LinkedHashMap<>();
        values.forEach((key, val) -> res.put(key, AdvancedGraphLiveValue.from(val)));
        return res;
    }

    // Require a full baseline after an editor or direct snapshot replaces a player's HUD values
    void forget(UUID playerId){ baselines.remove(playerId); }

    // Release players who disconnected, removed their goggles, or changed controllers
    void retain(Set<UUID> playerIds){ baselines.keySet().retainAll(playerIds); }

    // Release all baselines after a graph or controller lifecycle change
    void clear(){ baselines.clear(); }

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
    private record Baseline(Object connection, AdvancedGraphDocument graph, int graphRevision, UUID streamId,
                            long sequence, long lastFullTick, Map<String, AdvancedGraphDocument.Value> inputs,
                            Map<String, AdvancedGraphDocument.Value> outputs, Metadata metadata){}
}
