package com.rieno.gadgetsandgizmos.content.advanced;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerBlockEntity;
import com.rieno.gadgetsandgizmos.lib.graph.GraphDraftSession;
import com.rieno.gadgetsandgizmos.lib.graph.GraphRuntimeJournal;
import com.rieno.gadgetsandgizmos.lib.nbt.CompoundTagDelta;
import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

// Adapt one menu's unsaved graph to the shared session and execution APIs
public final class AdvancedGraphDraftSession{
/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        DEFAULTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    private final GraphRuntime runtime;
    private final GraphRuntime applied;
    private final UUID playerId;
    private final AdvancedContraptionControllerBlockEntity controller;
    private boolean followingApplied;
    private Object appliedProgram;
    private boolean resetPublication = true;
    private boolean closed;
    private final GraphDraftSession<AdvancedGraphDocument> session;
    private final com.rieno.gadgetsandgizmos.lib.graph.GraphInputEvents.Subscription inputSubscription;
    private final com.rieno.gadgetsandgizmos.lib.chat.ServerChatApi.Subscription chatSubscription;
    private CompoundTag document = new CompoundTag();
    private UUID streamId;
    private long sequence;
    private java.util.List<GraphRuntimeJournal.Event<AdvancedGraphDocument.Value>> initialValues = java.util.List.of();

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                       FUNCTIONS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    public AdvancedGraphDraftSession(AdvancedContraptionControllerBlockEntity controller, UUID playerId){
        this.playerId = playerId;
        this.controller = controller;
        runtime = new GraphRuntime(controller, true);
        applied = controller.graphRuntimeForDraftSession();
        applied.retainRuntimeObservation();
        session = new GraphDraftSession<>(new GraphDraftSession.Host<>(){
            @Override public void reconcile(AdvancedGraphDocument prev, AdvancedGraphDocument next){
                boolean follow = applied.matchesSimulationGraph(next);
                if(follow != followingApplied){ streamId = null; sequence = 0; }
                if(follow && !followingApplied){
                    runtime.clear();
                    var initial = applied.runtimeChanges(null, 0);
                    streamId = initial.streamId();
                    sequence = initial.sequence();
                    initialValues = initial.events();
                    resetPublication = true;
                }
                if(!follow && (prev == null || followingApplied)){
                    runtime.inheritSimulationState(applied, next);
                    primeReader();
                }
                else if(!follow) runtime.replaceSimulationGraph(prev, next);
                followingApplied = follow;
                appliedProgram = applied.executionIdentity();
            }
            @Override public void tick(AdvancedGraphDocument graph){
                if(followingApplied && appliedProgram != applied.executionIdentity()){
                    followingApplied = applied.matchesSimulationGraph(graph);
                    appliedProgram = applied.executionIdentity();
                    if(!followingApplied){ runtime.inheritSimulationState(applied, graph); primeReader(); }
                }
                if(followingApplied) applied.sampleRuntimePorts(null);
                else runtime.tickSimulation(graph, playerId);
            }
            @Override public void close(){ runtime.clear(); }
        });
        var initial = runtime.runtimeChanges(null, 0);
        streamId = initial.streamId();
        sequence = initial.sequence();
        inputSubscription = applied.externalInputs().subscribe(evt -> {
            if(closed || followingApplied || session.graph() == null) return;
            if(evt.id().startsWith("channel:")) return;
            var val = evt.value() == null ? AdvancedGraphDocument.Value.number(0)
                    : new AdvancedGraphDocument.Value(evt.value().type(), evt.value().payload(), evt.value().entryOrder());
            runtime.enqueueExternal(evt.id(), val, evt.playerId());
        });
        chatSubscription = com.rieno.gadgetsandgizmos.lib.chat.ServerChatApi.listen(controller.getLevel().getServer(), message -> {
            if(!closed && !followingApplied && session.graph() != null) runtime.captureChatMessage(session.graph(), message);
        });
    }

    // Apply mutations atomically to this session without saving or applying the controller
    public boolean edit(long base, long revision, boolean full, CompoundTag patch){
        if(base != session.revision() || revision <= base) return false;
        CompoundTag next = full ? patch.copy() : document.copy();
        if(!full) CompoundTagDelta.apply(next, patch);
        AdvancedGraphDocument graph = AdvancedGraphDocument.fromTag(next);
        if(!controller.canEditGraph(session.graph() == null ? controller.getDraftGraph() : session.graph(), graph)) return false;
        if(graph.totalNodeCount() > AdvancedGraphDocument.maxNodes()
                || graph.totalEdgeCount() > AdvancedGraphDocument.MAX_EDGES) return false;
        graph.setRevision((int)Math.min(Integer.MAX_VALUE, revision));
        if(!session.replace(base, revision, graph)) return false;
        document = next;
        return true;
    }

    public void tick(long tick){ session.tick(tick); }
    public long revision(){ return session.revision(); }

    public GraphRuntimeJournal.Update<AdvancedGraphDocument.Value> changes(){
        var update = (followingApplied ? applied : runtime).runtimeChanges(streamId, sequence);
        streamId = update.streamId();
        sequence = update.sequence();
        if(resetPublication){
            resetPublication = false;
            var events = new java.util.ArrayList<GraphRuntimeJournal.Event<AdvancedGraphDocument.Value>>();
            if(!update.reset()) events.addAll(initialValues);
            events.addAll(update.events());
            initialValues = java.util.List.of();
            return new GraphRuntimeJournal.Update<>(update.streamId(), update.sequence(), true, events);
        }
        return update;
    }

    private void primeReader(){
        var initial = runtime.runtimeChanges(null, 0);
        streamId = initial.streamId();
        sequence = initial.sequence();
        initialValues = initial.events();
        resetPublication = true;
    }

    public void binding(String id, boolean active){
        if(followingApplied){
            runtime.inheritSimulationState(applied, session.graph());
            primeReader();
            followingApplied = false;
        }
        runtime.withInteractionPlayer(playerId, () -> { runtime.simulationBinding(id, active); return null; });
    }
    public void close(){
        if(closed) return;
        closed = true;
        inputSubscription.close();
        chatSubscription.close();
        session.close();
        applied.releaseRuntimeObservation();
    }
}
