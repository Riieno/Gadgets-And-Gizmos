package com.rieno.gadgetsandgizmos.graph.compile;

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphValidator;
import com.rieno.gadgetsandgizmos.graph.struct.AbstractGraphRuntime;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class DummyRuntime extends AbstractGraphRuntime {
    @Override
    public void enqueue(String eventId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void enqueue(String eventId, @Nullable UUID triggeringPlayerId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean tryEnqueue(String eventId, @Nullable UUID triggeringPlayerId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void enqueuePhysicalInteraction(ServerPlayer player, boolean active, boolean remote, String keyPressed) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void enqueueNamedControllerEvent(String name, AdvancedGraphDocument.Value data) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean enqueueHudInteraction(String nodeId, String interactionId, AdvancedGraphDocument.Value val) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean enqueueHudButtonInteraction(String nodeId, String interactionId, long serverGameTime) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean enqueueHudToggleInteraction(String nodeId, String interactionId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean setBindingActive(String bindingId, boolean active) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean hasPendingWork() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean needsRegularTick(AdvancedGraphDocument graph) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean needsSimulationTick(AdvancedGraphDocument graph) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean needsBindingPolling(AdvancedGraphDocument graph) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Set<String> polledBindings(AdvancedGraphDocument graph) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Set<String> graphOwnedBindings(AdvancedGraphDocument graph) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void tick(AdvancedGraphDocument graph) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void tick(AdvancedGraphDocument graph, boolean sampleUnconnectedPassiveOutputs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void compile(AdvancedGraphDocument graph) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException();
    }

    @Override
    public long liveValueRevision() {
        throw new UnsupportedOperationException();
    }

    @Override
    public CompoundTag createShutdownSnapshot() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void restoreShutdownSnapshot(CompoundTag snapshot) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void prepareForServerShutdown() {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<AdvancedGraphValidator.Diagnostic> diagnostics() {
        throw new UnsupportedOperationException();
    }

    @Override
    public AdvancedGraphDocument.Value previewInput(AdvancedGraphDocument graph, AdvancedGraphDocument.Node node, String port) {
        throw new UnsupportedOperationException();
    }

    @Override
    public AdvancedGraphDocument.Value previewOutput(AdvancedGraphDocument graph, AdvancedGraphDocument.Node node, String port) {
        throw new UnsupportedOperationException();
    }

    @Override
    public AdvancedGraphDocument.Value previewFunctionInput(AdvancedGraphDocument graph, String functionId, AdvancedGraphDocument.Node node, String port) {
        throw new UnsupportedOperationException();
    }

    @Override
    public AdvancedGraphDocument.Value previewFunctionOutput(AdvancedGraphDocument graph, String functionId, AdvancedGraphDocument.Node node, String port) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void beginPreviewSample(AdvancedGraphDocument graph) {
        throw new UnsupportedOperationException();
    }

    @Override
    public AdvancedGraphDocument.Value liveInput(String nodeId, String port) {
        throw new UnsupportedOperationException();
    }

    @Override
    public AdvancedGraphDocument.Value liveOutput(String nodeId, String port) {
        throw new UnsupportedOperationException();
    }

    @Override
    public long executionPulse(String edgeKey) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Map<String, AdvancedGraphDocument.Value> liveInputs() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Map<String, AdvancedGraphDocument.Value> liveOutputs() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Map<String, Long> executionPulses() {
        throw new UnsupportedOperationException();
    }
}
