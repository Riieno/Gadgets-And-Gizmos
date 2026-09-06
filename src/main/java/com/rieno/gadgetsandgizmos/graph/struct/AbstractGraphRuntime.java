package com.rieno.gadgetsandgizmos.graph.struct;

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphValidator;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public abstract class AbstractGraphRuntime {


    public abstract void enqueue(String eventId);

    public abstract void enqueue(String eventId, @Nullable UUID triggeringPlayerId);

    public abstract boolean tryEnqueue(String eventId, @Nullable UUID triggeringPlayerId);

    public abstract void enqueuePhysicalInteraction(ServerPlayer player, boolean active,
                                                    boolean remote, String keyPressed);

    public abstract void enqueueNamedControllerEvent(String name, AdvancedGraphDocument.Value data);

    public abstract boolean enqueueHudInteraction(String nodeId, String interactionId,
                                                  AdvancedGraphDocument.Value val);

    public abstract boolean enqueueHudButtonInteraction(String nodeId, String interactionId,
                                                        long serverGameTime);

    public abstract boolean enqueueHudToggleInteraction(String nodeId, String interactionId);

    public abstract boolean setBindingActive(String bindingId, boolean active);

    public abstract boolean hasPendingWork();

    public abstract boolean needsRegularTick(AdvancedGraphDocument graph);

    public abstract boolean needsSimulationTick(AdvancedGraphDocument graph);

    public abstract boolean needsBindingPolling(AdvancedGraphDocument graph);

    public abstract Set<String> polledBindings(AdvancedGraphDocument graph);

    public abstract Set<String> graphOwnedBindings(AdvancedGraphDocument graph);

    public abstract void tick(AdvancedGraphDocument graph);

    public abstract void tick(AdvancedGraphDocument graph, boolean sampleUnconnectedPassiveOutputs);

    public abstract void compile(AdvancedGraphDocument graph);

    public abstract void clear();

    public abstract long liveValueRevision();

    public abstract CompoundTag createShutdownSnapshot();

    public abstract void restoreShutdownSnapshot(CompoundTag snapshot);

    public abstract void prepareForServerShutdown();

    public abstract List<AdvancedGraphValidator.Diagnostic> diagnostics();

    public abstract AdvancedGraphDocument.Value previewInput(AdvancedGraphDocument
                                                                 graph, AdvancedGraphDocument.Node node, String port);

    public abstract AdvancedGraphDocument.Value previewOutput(AdvancedGraphDocument
                                                                  graph, AdvancedGraphDocument.Node node, String port);

    public abstract AdvancedGraphDocument.Value previewFunctionInput(
        AdvancedGraphDocument graph, String functionId,
        AdvancedGraphDocument.Node node, String port);

    public abstract AdvancedGraphDocument.Value previewFunctionOutput(
        AdvancedGraphDocument graph, String functionId,
        AdvancedGraphDocument.Node node, String port);

    public abstract void beginPreviewSample(AdvancedGraphDocument graph);

    public abstract AdvancedGraphDocument.Value liveInput(String nodeId, String port);

    public abstract AdvancedGraphDocument.Value liveOutput(String nodeId, String port);

    public abstract long executionPulse(String edgeKey);

    ;

    public abstract Map<String, AdvancedGraphDocument.Value> liveInputs();

    ;

    public abstract Map<String, AdvancedGraphDocument.Value> liveOutputs();

    ;

    public abstract Map<String, Long> executionPulses();
    /*

    ;
    public abstract AdvancedGraphDocument.Value evaluate(Frame frame,
                                                int[] operations);

    public abstract AdvancedGraphDocument.Value evaluate(Frame frame,
                                                int[] operations);
    */

}
