package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDraftSession;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphNodeFactory;
import com.rieno.gadgetsandgizmos.content.advanced.GraphRuntime;
import com.rieno.gadgetsandgizmos.lib.chat.ServerChatApi;
import com.rieno.gadgetsandgizmos.lib.graph.GraphRuntimeJournal;
import com.rieno.gadgetsandgizmos.lib.nbt.CompoundTagDelta;
import com.rieno.gadgetsandgizmos.lib.control.DistanceIndependentLink;
import com.rieno.gadgetsandgizmos.lib.control.RedstoneLinkReceiver;
import com.simibubi.create.Create;
import com.simibubi.create.content.redstone.link.IRedstoneLinkable;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler;
import net.createmod.catnip.data.Couple;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

// Exercise unsaved execution without changing the applied controller or its physical commands
@GameTestHolder("createthrusters")
@PrefixGameTestTemplate(false)
public final class GraphDraftSessionGameTests{
/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                       FUNCTIONS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    // A key node absent from the applied graph must execute only within its draft
    @GameTest(template = "acc_presentation_empty", timeoutTicks = 20)
    public static void unsavedWirelessInputsRebindAndReleaseReceivers(GameTestHelper helper){
        var controller = controller(helper);
        var graph = counterGraph();
        graph.nodes().removeIf(node -> node.id().equals("tick"));
        graph.edges().clear();
        var input = node("wireless", "wireless_frequency_input");
        input.data().putString("FrequencyFirst", "minecraft:diamond");
        graph.nodes().add(input);
        graph.edges().add(new AdvancedGraphDocument.Edge("received", "wireless", "exec", "counter", "exec"));
        var source = new WirelessSource(controller.getBlockPos(), new ItemStack(Items.DIAMOND));
        var runtime = new GraphRuntime(controller, true);
        Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(helper.getLevel(), source);
        try{
            runtime.replaceSimulationGraph(null, graph);
            runtime.tickSimulation(graph, null);
            helper.assertTrue(runtime.liveOutput("counter", "value").asNumber() == 1, "A new wireless route did not execute");
            helper.assertTrue(runtime.liveOutput("wireless", "value").asNumber() == 9, "Wireless strength changed in simulation");
            helper.assertTrue(controller.getCustomKeyEntries().isEmpty(), "Draft wireless input installed a real channel");
            var edited = graph.copy();
            edited.nodes().stream().filter(node -> node.id().equals("wireless")).findFirst().orElseThrow()
                    .data().putString("FrequencyFirst", "minecraft:emerald");
            runtime.replaceSimulationGraph(graph, edited);
            runtime.tickSimulation(edited, null);
            helper.assertTrue(runtime.liveOutput("wireless", "value").asNumber() == 0, "Rebinding retained the previous frequency");
            helper.assertTrue(Create.REDSTONE_LINK_NETWORK_HANDLER.getNetworkOf(helper.getLevel(), source).stream()
                    .noneMatch(link -> link instanceof RedstoneLinkReceiver), "Rebinding left the old receiver registered");
            runtime.clear();
            var probe = new WirelessSource(controller.getBlockPos(), new ItemStack(Items.EMERALD));
            helper.assertTrue(Create.REDSTONE_LINK_NETWORK_HANDLER.getNetworkOf(helper.getLevel(), probe).stream()
                    .noneMatch(link -> link instanceof RedstoneLinkReceiver), "Closing a draft leaked its receiver");
        }finally{
            runtime.clear();
            Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(helper.getLevel(), source);
        }
        helper.succeed();
    }

    // An unbound local input reads physical neighbours without requiring an applied route
    @GameTest(template = "acc_presentation_empty", timeoutTicks = 20)
    public static void unsavedLocalRedstoneReadsPhysicalNeighbours(GameTestHelper helper){
        var controller = controller(helper);
        helper.setBlock(new BlockPos(2, 1, 2).relative(Direction.NORTH), Blocks.REDSTONE_BLOCK);
        var graph = counterGraph();
        graph.nodes().removeIf(node -> node.id().equals("tick"));
        graph.edges().clear();
        var input = node("redstone", "local_redstone_input");
        input.data().putString("Face", "north");
        graph.nodes().add(input);
        graph.edges().add(new AdvancedGraphDocument.Edge("received", "redstone", "exec", "counter", "exec"));
        var runtime = new GraphRuntime(controller, true);
        try{
            runtime.replaceSimulationGraph(null, graph);
            runtime.tickSimulation(graph, null);
            helper.assertTrue(runtime.liveOutput("redstone", "value").asNumber() == 15, "Local draft input missed its physical source");
            helper.assertTrue(runtime.liveOutput("counter", "value").asNumber() == 1, "Local redstone failed to emit its input pulse");
            helper.assertTrue(controller.getCustomKeyEntries().isEmpty(), "Local draft input installed a real channel");
        }finally{ runtime.clear(); }
        helper.succeed();
    }

    // Applying a new local input keeps the same source and edge behavior used by its draft
    @GameTest(template = "acc_presentation_empty", timeoutTicks = 20)
    public static void appliedAndDraftLocalInputsUseTheSamePhysicalSource(GameTestHelper helper){
        var controller = controller(helper);
        helper.setBlock(new BlockPos(2, 1, 2).relative(Direction.NORTH), Blocks.REDSTONE_BLOCK);
        var graph = counterGraph();
        graph.nodes().removeIf(node -> node.id().equals("tick"));
        graph.edges().clear();
        var input = node("redstone", "local_redstone_input");
        input.data().putString("Face", "north");
        graph.nodes().add(input);
        graph.edges().add(new AdvancedGraphDocument.Edge("received", "redstone", "exec", "counter", "exec"));
        helper.assertTrue(controller.saveDraft(graph, controller.getDraftGraph().revision()) && controller.applyDraft(),
                "The local input graph was rejected");
        helper.runAtTickTime(4, () -> {
            var runtime = controller.graphRuntimeForDraftSession();
            var active = controller.getActiveGraph();
            var current = active.nodes().stream().filter(node -> node.id().equals("redstone")).findFirst().orElseThrow();
            helper.assertTrue(runtime.previewOutput(active, current, "value").asNumber() == 15,
                    "Applied local input disagreed with its draft");
            helper.assertTrue(runtime.liveOutput("counter", "value").asNumber() == 1,
                    "Applied local input lost or repeated its rising edge");
            helper.succeed();
        });
    }

    // A key node absent from the applied graph must execute only within its draft
    @GameTest(template = "acc_presentation_empty", timeoutTicks = 20)
    public static void draftKeyboardEventsStayLocal(GameTestHelper helper){
        var controller = controller(helper);
        var graph = counterGraph();
        graph.nodes().removeIf(node -> node.id().equals("tick"));
        graph.edges().clear();
        var key = node("key", "event_key");
        key.data().putString("BindingId", "draft_key");
        graph.nodes().add(key);
        graph.edges().add(new AdvancedGraphDocument.Edge("press", "key", "exec", "counter", "exec"));
        UUID player = UUID.randomUUID();
        var session = new AdvancedGraphDraftSession(controller, player);
        helper.assertTrue(session.edit(-1, 0, true, graph.toTag()), "The keyboard draft was rejected");
        session.binding("draft_key", true);
        session.tick(helper.getLevel().getGameTime());
        var update = session.changes();
        helper.assertTrue(counter(update) == 1, "The draft key did not execute its event");
        helper.assertTrue(update.events().stream().anyMatch(evt -> evt.channel() == GraphRuntimeJournal.Channel.PULSE
                && player.equals(evt.playerId())), "The key pulse lost its player");
        helper.assertTrue(controller.getGraphBindingValue("draft_key") == 0, "Draft input changed an applied binding");
        session.close();
        helper.succeed();
    }

    // Named events reach new draft listeners even when the applied graph has none
    @GameTest(template = "acc_presentation_empty", timeoutTicks = 20)
    public static void unsavedNamedEventsKeepDataAndPlayer(GameTestHelper helper){
        var controller = controller(helper);
        var graph = counterGraph();
        graph.nodes().removeIf(node -> node.id().equals("tick"));
        graph.edges().clear();
        var event = node("named", "event_named_controller");
        event.data().putString("Event", "draft_test");
        graph.nodes().add(event);
        graph.edges().add(new AdvancedGraphDocument.Edge("received", "named", "exec", "counter", "exec"));
        UUID player = UUID.randomUUID();
        var session = new AdvancedGraphDraftSession(controller, UUID.randomUUID());
        try{
            helper.assertTrue(session.edit(-1, 0, true, graph.toTag()), "The named-event draft was rejected");
            controller.receiveNamedControllerEvent("draft_test", AdvancedGraphDocument.Value.number(12), player);
            controller.receiveNamedControllerEvent("draft_test", AdvancedGraphDocument.Value.number(34), player);
            session.tick(helper.getLevel().getGameTime());
            var update = session.changes();
            helper.assertTrue(counter(update) == 2, "Repeated external events were lost");
            helper.assertTrue(update.events().stream().filter(evt -> evt.channel() == GraphRuntimeJournal.Channel.PULSE
                    && player.equals(evt.playerId())).count() == 2, "Named-event pulses lost their player");
            helper.assertTrue(update.events().stream().anyMatch(evt -> evt.channel() == GraphRuntimeJournal.Channel.OUTPUT
                    && evt.key().equals("named:data") && evt.value().asNumber() == 34), "Named-event payload was lost");
        }finally{ session.close(); }
        helper.succeed();
    }

    // An unsaved watcher captures actual chat without hiding messages or changing the live graph
    @GameTest(template = "acc_presentation_empty", timeoutTicks = 20)
    public static void unsavedChatWatcherReceivesLiveMessages(GameTestHelper helper){
        var controller = controller(helper);
        var graph = counterGraph();
        graph.nodes().removeIf(node -> node.id().equals("tick"));
        graph.edges().clear();
        var watcher = node("watch", "watch_chat");
        String prefix = "!draft_" + UUID.randomUUID();
        watcher.data().getCompound("Defaults").put("start_word", AdvancedGraphDocument.Value.string(prefix).toTag());
        watcher.data().getCompound("Defaults").put("hide_message", AdvancedGraphDocument.Value.bool(true).toTag());
        graph.nodes().add(watcher);
        graph.edges().add(new AdvancedGraphDocument.Edge("received", "watch", "exec", "counter", "exec"));
        UUID player = UUID.randomUUID();
        var session = new AdvancedGraphDraftSession(controller, UUID.randomUUID());
        try{
            helper.assertTrue(session.edit(-1, 0, true, graph.toTag()), "The chat draft was rejected");
            boolean hidden = ServerChatApi.receivePlayerChat(helper.getLevel().getServer(),
                    new ServerChatApi.Message(prefix + " first second", player, "DraftPlayer"));
            session.tick(helper.getLevel().getGameTime());
            var update = session.changes();
            helper.assertTrue(!hidden, "An unsaved watcher hid a real chat message");
            helper.assertTrue(counter(update) == 1, "An unsaved watcher did not execute");
            helper.assertTrue(update.events().stream().anyMatch(evt -> evt.channel() == GraphRuntimeJournal.Channel.PULSE
                    && player.equals(evt.playerId())), "The chat event lost its player");
            helper.assertTrue(controller.getActiveGraph().nodes().stream().noneMatch(node -> node.id().equals("watch")),
                    "Chat simulation changed the applied graph");
        }finally{ session.close(); }
        helper.succeed();
    }

    // A new target route samples its host directly and retains physical input ownership
    @GameTest(template = "acc_presentation_empty", timeoutTicks = 20)
    public static void unsavedTargetInputsDoNotRequireAppliedBindings(GameTestHelper helper){
        double[] signal = {0};
        UUID actor = UUID.randomUUID();
        var controller = new AdvancedContraptionControllerBlockEntity(helper.absolutePos(new BlockPos(2, 1, 2)),
                CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER.get().defaultBlockState()){
            @Override public AdvancedGraphDocument.Value getGraphTargetData(AdvancedGraphDocument.Node node, String port){
                return AdvancedGraphDocument.Value.number(signal[0]);
            }
            @Override public UUID graphInputPlayer(AdvancedGraphDocument.Node node){ return actor; }
        };
        controller.setLevel(helper.getLevel());
        var graph = counterGraph();
        graph.nodes().removeIf(node -> node.id().equals("tick"));
        graph.edges().clear();
        var input = node("target", "discovered_target_input");
        input.data().putString("BindingId", "unsaved_target");
        graph.nodes().add(input);
        graph.edges().add(new AdvancedGraphDocument.Edge("changed", "target", "exec", "counter", "exec"));
        var runtime = new GraphRuntime(controller, true);
        try{
            runtime.replaceSimulationGraph(null, graph);
            var initial = runtime.runtimeChanges(null, 0);
            runtime.tickSimulation(graph, UUID.randomUUID());
            var idle = runtime.runtimeChanges(initial.streamId(), initial.sequence());
            helper.assertTrue(idle.events().stream().noneMatch(evt -> evt.channel() == GraphRuntimeJournal.Channel.PULSE),
                    "An initially inactive input emitted a synthetic falling edge");
            signal[0] = 1;
            runtime.tickSimulation(graph, UUID.randomUUID());
            var active = runtime.runtimeChanges(idle.streamId(), idle.sequence());
            helper.assertTrue(runtime.liveOutput("counter", "value").asNumber() == 1,
                    "A new target input depended on an applied binding");
            helper.assertTrue(active.events().stream().anyMatch(evt -> evt.channel() == GraphRuntimeJournal.Channel.PULSE
                    && actor.equals(evt.playerId())), "Target input ownership was replaced by the editor player");
            helper.assertTrue(controller.getGraphBindingValue("unsaved_target") == 0,
                    "Draft polling installed a real input route");
        }finally{ runtime.clear(); }
        helper.succeed();
    }

    // Read-only sampling refreshes physical values even when the source remains active
    @GameTest(template = "acc_presentation_empty", timeoutTicks = 20)
    public static void appliedInputSamplingRefreshesValuesWithoutExecuting(GameTestHelper helper){
        double[] signal = {0.6D};
        var controller = new AdvancedContraptionControllerBlockEntity(helper.absolutePos(new BlockPos(2, 1, 2)),
                CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER.get().defaultBlockState()){
            @Override public double getGraphBindingValue(String binding){ return signal[0]; }
        };
        controller.setLevel(helper.getLevel());
        var graph = counterGraph();
        graph.nodes().removeIf(node -> node.id().equals("tick"));
        graph.edges().clear();
        var input = node("input", "controller_channel_input");
        input.data().putString("BindingId", "physical");
        graph.nodes().add(input);
        graph.edges().add(new AdvancedGraphDocument.Edge("changed", "input", "exec", "counter", "exec"));
        var runtime = new GraphRuntime(controller);
        try{
            runtime.compile(graph);
            runtime.setBindingActive("physical", true);
            runtime.tick(graph, false);
            runtime.sampleRuntimePorts(null);
            helper.assertTrue(runtime.liveOutput("input", "value").asNumber() == 0.6D, "The first physical sample was lost");
            signal[0] = 0.9D;
            runtime.sampleRuntimePorts(null);
            helper.assertTrue(runtime.liveOutput("input", "value").asNumber() == 0.9D,
                    "An active physical input retained an older sampled value");
            helper.assertTrue(runtime.liveOutput("counter", "value").asNumber() == 1,
                    "Refreshing a physical value executed the graph");
        }finally{ runtime.clear(); }
        helper.succeed();
    }

    // Two editors retain separate unsaved state and inputs while observing the same host
    @GameTest(template = "acc_presentation_empty", timeoutTicks = 20)
    public static void simultaneousDraftsKeepInputsAndCountersSeparate(GameTestHelper helper){
        var controller = controller(helper);
        var graph = counterGraph();
        graph.nodes().removeIf(node -> node.id().equals("tick"));
        graph.edges().clear();
        var key = node("key", "event_key");
        key.data().putString("BindingId", "private_key");
        graph.nodes().add(key);
        graph.edges().add(new AdvancedGraphDocument.Edge("pressed", "key", "exec", "counter", "exec"));
        var first = new AdvancedGraphDraftSession(controller, UUID.randomUUID());
        var second = new AdvancedGraphDraftSession(controller, UUID.randomUUID());
        try{
            helper.assertTrue(first.edit(-1, 0, true, graph.toTag()) && second.edit(-1, 0, true, graph.toTag()),
                    "A simultaneous draft was rejected");
            first.binding("private_key", true);
            first.tick(helper.getLevel().getGameTime());
            second.tick(helper.getLevel().getGameTime());
            helper.assertTrue(counter(first.changes()) == 1, "The first draft missed its key pulse");
            var other = second.changes();
            helper.assertTrue(counter(other) == 0, "One editor's counter changed another editor's draft");
            helper.assertTrue(other.events().stream().noneMatch(evt -> evt.channel() == GraphRuntimeJournal.Channel.PULSE),
                    "A private input pulse escaped into another draft");
            helper.assertTrue(controller.getActiveGraph().nodes().isEmpty(), "Editing drafts changed the applied graph");
        }finally{ first.close(); second.close(); }
        helper.succeed();
    }

    // Opening an applied graph observes its real counter before an unsaved branch inherits it
    @GameTest(template = "acc_presentation_empty", timeoutTicks = 30)
    public static void matchingDraftObservesAndInheritsAppliedState(GameTestHelper helper){
        var controller = controller(helper);
        var graph = counterGraph();
        var unused = node("unused", "constant_number");
        unused.data().putDouble("Value", 57);
        graph.nodes().add(unused);
        helper.assertTrue(controller.saveDraft(graph, controller.getDraftGraph().revision()) && controller.applyDraft(),
                "The applied fixture was rejected");
        helper.runAtTickTime(4, () -> {
            double actual = controller.graphRuntimeForDraftSession().liveOutput("counter", "value").asNumber();
            var current = controller.getActiveGraph();
            var session = new AdvancedGraphDraftSession(controller, UUID.randomUUID());
            var original = current.toTag();
            helper.assertTrue(session.edit(-1, 0, true, original), "Matching draft was rejected");
            session.tick(helper.getLevel().getGameTime());
            var update = session.changes();
            helper.assertTrue(counter(update) == actual, "Opening the editor restarted the applied counter");
            helper.assertTrue(update.events().stream().anyMatch(evt -> evt.channel() == GraphRuntimeJournal.Channel.OUTPUT
                    && evt.key().equals("unused:value") && evt.value().asNumber() == 57),
                    "An applied graph omitted an unconnected live port");
            helper.assertTrue(controller.graphRuntimeForDraftSession().liveOutput("counter", "value").asNumber() == actual,
                    "Sampling applied ports stepped the real counter");
            current.nodes().add(node("added", "constant_number"));
            helper.assertTrue(session.edit(0, 1, false, CompoundTagDelta.between(original, current.toTag())),
                    "The independent draft was rejected");
            helper.runAtTickTime(6, () -> {
                session.tick(helper.getLevel().getGameTime());
                helper.assertTrue(counter(session.changes()) == actual + 1, "The unsaved branch lost applied state");
                session.close();
                helper.succeed();
            });
        });
    }

    // Recovery serialization visits pending work directly without losing order or authenticated actors
    @GameTest(template = "acc_presentation_empty", timeoutTicks = 20)
    public static void recoveryPreservesQueuedEventsAndDelayedReleases(GameTestHelper helper){
        var controller = controller(helper);
        var graph = counterGraph();
        var runtime = new GraphRuntime(controller, true);
        var restored = new GraphRuntime(controller, true);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        try{
            runtime.replaceSimulationGraph(null, graph);
            runtime.enqueueExternal("named:recover", AdvancedGraphDocument.Value.number(1), first);
            runtime.enqueueExternal("named:recover", AdvancedGraphDocument.Value.number(2), second);
            runtime.withInteractionPlayer(first, () -> runtime.enqueueHudButtonRelease("button", "pressed", 100));
            var saved = runtime.createShutdownSnapshot();
            var events = saved.getList("Events", 10);
            helper.assertTrue(events.size() == 2 && events.getCompound(0).getUUID("Player").equals(first)
                    && events.getCompound(1).getUUID("Player").equals(second), "Recovery changed queued event order or ownership");
            var delayed = saved.getList("Scheduled", 10);
            helper.assertTrue(delayed.size() == 1 && delayed.getCompound(0).getLong("Tick") == 101
                    && delayed.getCompound(0).getUUID("Player").equals(first), "Recovery changed the delayed release");
            restored.replaceSimulationGraph(null, graph.copy());
            restored.restoreShutdownSnapshot(saved);
            helper.assertTrue(saved.equals(restored.createShutdownSnapshot()), "Recovery serialization changed its saved format or contents");
        }finally{ runtime.clear(); restored.clear(); }
        helper.succeed();
    }

    // Compact in-memory history keeps the existing saved data and rollback ordering
    @GameTest(template = "acc_presentation_empty", timeoutTicks = 20)
    public static void compactHistoryPreservesSavedVersions(GameTestHelper helper){
        var history = new com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphVersionHistory();
        for(int idx = 1; idx <= 5; idx++){
            var graph = counterGraph();
            graph.setRevision(idx);
            graph.nodes().add(node("version_" + idx, "constant_number"));
            history.push(graph, idx * 100L);
        }
        var data = history.toTag();
        var loaded = new com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphVersionHistory();
        loaded.fromTag(data);
        helper.assertTrue(data.equals(loaded.toTag()), "Saved graph history changed format or contents");
        var restored = loaded.rollback(2, counterGraph());
        helper.assertTrue(restored != null && restored.revision() == 3
                && restored.nodes().stream().anyMatch(node -> node.id().equals("version_3")), "Rollback selected the wrong revision");
        helper.succeed();
    }

    // Unsaved SCM, shipping and worker actions emit no fabricated execution results
    @GameTest(template = "acc_presentation_empty", timeoutTicks = 20)
    public static void unsavedActionsStayInactiveWithoutSuccessOrContinuation(GameTestHelper helper){
        var controller = controller(helper);
        var graph = counterGraph();
        graph.edges().clear();
        String[][] actions = {{"dock", "ship_dock"}, {"initialize", "ship_scan_configuration"}, {"worker", "request_worker_task"},
                {"cancel", "cancel_worker_task"}, {"schedule", "shipping_start"}};
        for(String[] action : actions){
            graph.nodes().add(node(action[0], action[1]));
            graph.nodes().add(node("after_" + action[0], "count"));
            graph.edges().add(new AdvancedGraphDocument.Edge("start_" + action[0], "tick", "exec", action[0], "exec"));
            for(String port : java.util.List.of("exec", "complete", "accepted", "rejected", "failed", "cancelled", "not_found")){
                graph.edges().add(new AdvancedGraphDocument.Edge(action[0] + "_" + port, action[0], port, "after_" + action[0], "exec"));
            }
        }
        var runtime = new GraphRuntime(controller, true);
        try{
            runtime.replaceSimulationGraph(null, graph);
            var initial = runtime.runtimeChanges(null, 0);
            runtime.tickSimulation(graph, null);
            var update = runtime.runtimeChanges(initial.streamId(), initial.sequence());
            for(String[] action : actions){
                helper.assertTrue(runtime.liveOutput("after_" + action[0], "value").asNumber() == 0,
                        "An unsaved action fabricated an outgoing execution pulse: " + action[0]);
                helper.assertTrue(initial.events().stream().anyMatch(evt -> evt.channel() == GraphRuntimeJournal.Channel.STATUS
                        && evt.key().equals(action[0]) && evt.value() != null), "An inactive action omitted its status: " + action[0]);
            }
            helper.assertTrue(!runtime.liveOutput("dock", "success").asBoolean(), "An unsaved docking action reported success");
            helper.assertTrue(runtime.liveOutput("initialize", "progress").asString().equals("Inactive until applied"),
                    "An inactive docking action displayed live command progress");
            helper.assertTrue(runtime.liveOutput("worker", "request").asString().isEmpty(), "An unsaved worker action created a request");
            helper.assertTrue(runtime.liveOutput("worker", "failure_reason").asString().equals("unsaved_graph"),
                    "An inactive worker action reported a successful request");
            var edited = graph.copy();
            edited.nodes().removeIf(node -> node.id().equals("dock"));
            edited.edges().removeIf(edge -> edge.fromNode().equals("dock") || edge.toNode().equals("dock"));
            edited.nodes().add(node("dock", "constant_number"));
            runtime.replaceSimulationGraph(graph, edited);
            runtime.tickSimulation(edited, null);
            var changed = runtime.runtimeChanges(update.streamId(), update.sequence());
            helper.assertTrue(changed.events().stream().anyMatch(evt -> evt.channel() == GraphRuntimeJournal.Channel.STATUS
                    && evt.key().equals("dock") && evt.value() == null), "Replacing an action left its inactive status behind");
        }finally{ runtime.clear(); }
        helper.succeed();
    }

    // A disabled action cannot present a previously saved successful result as an unsaved execution
    @GameTest(template = "acc_presentation_empty", timeoutTicks = 20)
    public static void inactiveActionsDoNotReusePersistedSuccessOrRequests(GameTestHelper helper){
        var graph = new AdvancedGraphDocument();
        graph.nodes().add(node("dock", "ship_dock"));
        graph.nodes().add(node("worker", "request_worker_task"));
        var runtime = new GraphRuntime(controller(helper), true);
        try{
            runtime.replaceSimulationGraph(null, graph);
            var saved = runtime.createShutdownSnapshot();
            saved.getCompound("LiveOutputs").put("dock:success", AdvancedGraphDocument.Value.bool(true).toTag());
            saved.getCompound("LiveOutputs").put("worker:request", AdvancedGraphDocument.Value.string(UUID.randomUUID().toString()).toTag());
            runtime.restoreShutdownSnapshot(saved);
            runtime.tickSimulation(graph, null);
            helper.assertTrue(!runtime.liveOutput("dock", "success").asBoolean(), "An inactive action reused a saved success result");
            helper.assertTrue(runtime.liveOutput("worker", "request").asString().isEmpty(), "An inactive action reused a saved worker request");
        }finally{ runtime.clear(); }
        helper.succeed();
    }

    // Simulated flight commands cannot change real approach or departure state
    @GameTest(template = "acc_presentation_empty", timeoutTicks = 20)
    public static void simulatedDockCommandsLeaveLiveStateUntouched(GameTestHelper helper) throws Exception{
        var controller = controller(helper);
        var field = AdvancedContraptionControllerBlockEntity.class.getDeclaredField("shipControlRuntime");
        field.setAccessible(true);
        var ship = (ShipControlModuleRuntime)field.get(controller);
        var before = ship.createShutdownSnapshot();
        var graph = counterGraph();
        graph.nodes().add(node("dock", "ship_dock"));
        graph.nodes().add(node("navigate", "ship_navigate"));
        graph.edges().add(new AdvancedGraphDocument.Edge("dock_pulse", "tick", "exec", "dock", "exec"));
        graph.edges().add(new AdvancedGraphDocument.Edge("navigate_pulse", "tick", "exec", "navigate", "exec"));
        var runtime = new GraphRuntime(controller, true);
        runtime.replaceSimulationGraph(null, graph);
        runtime.tickSimulation(graph, null);
        helper.assertTrue(before.equals(ship.createShutdownSnapshot()), "Draft commands changed live docking state");
        runtime.clear();
        helper.succeed();
    }

    // An unrelated edit preserves the counter and never persists or applies the draft
    @GameTest(template = "acc_presentation_empty", timeoutTicks = 30)
    public static void unsavedMutationsPreserveRunningState(GameTestHelper helper){
        var controller = controller(helper);
        int saved = controller.getDraftGraph().revision();
        int applied = controller.getActiveGraphRevision();
        var graph = counterGraph();
        var session = new AdvancedGraphDraftSession(controller, UUID.randomUUID());
        var original = graph.toTag();
        helper.assertTrue(session.edit(-1, 0, true, original), "Initial unsaved graph was rejected");
        session.tick(helper.getLevel().getGameTime());
        helper.assertTrue(counter(session.changes()) == 1, "The unsaved graph did not execute");
        graph.nodes().add(node("unrelated", "constant_number"));
        helper.assertTrue(session.edit(0, 1, false, CompoundTagDelta.between(original, graph.toTag())),
                "Unsaved mutation was rejected");
        helper.assertTrue(!session.edit(0, 2, true, original), "Stale edit replaced a newer draft");
        helper.runAtTickTime(3, () -> {
            session.tick(helper.getLevel().getGameTime());
            helper.assertTrue(counter(session.changes()) == 2, "An unrelated edit reset or double-stepped the counter");
            helper.assertTrue(controller.getDraftGraph().revision() == saved, "Simulation saved the graph");
            helper.assertTrue(controller.getActiveGraphRevision() == applied, "Simulation applied the graph");
            session.close();
            helper.succeed();
        });
    }

    // Separate executions of one wire in the same server tick must reach observers separately
    @GameTest(template = "acc_presentation_empty", timeoutTicks = 20)
    public static void repeatedExecutionsKeepIndividualPulses(GameTestHelper helper){
        var graph = counterGraph();
        var runtime = new GraphRuntime(controller(helper), true);
        runtime.replaceSimulationGraph(null, graph);
        var initial = runtime.runtimeChanges(null, 0);
        UUID viewer = UUID.randomUUID();
        runtime.tickSimulation(graph, viewer);
        runtime.tickSimulation(graph, viewer);
        var update = runtime.runtimeChanges(initial.streamId(), initial.sequence());
        long pulses = update.events().stream().filter(evt -> evt.channel() == GraphRuntimeJournal.Channel.PULSE).count();
        helper.assertTrue(pulses == 2, "Repeated executions in one tick were coalesced");
        helper.assertTrue(update.events().stream().filter(evt -> evt.channel() == GraphRuntimeJournal.Channel.PULSE)
                .allMatch(evt -> evt.playerId() == null), "Automatic ticks inherited the editor player's identity");
        helper.assertTrue(runtime.liveOutput("counter", "value").asNumber() == 2, "Pulse delivery altered execution");
        runtime.clear();
        helper.succeed();
    }

    // Removed delayed nodes cannot resume an old continuation under a reused id
    @GameTest(template = "acc_presentation_empty", timeoutTicks = 30)
    public static void removedDelayCannotExecuteReplacement(GameTestHelper helper){
        var graph = counterGraph();
        graph.nodes().removeIf(node -> node.id().equals("tick"));
        graph.edges().clear();
        graph.nodes().add(node("ready", "event_graph_ready"));
        graph.nodes().add(node("wait", "delay"));
        graph.edges().add(new AdvancedGraphDocument.Edge("start", "ready", "exec", "wait", "exec"));
        graph.edges().add(new AdvancedGraphDocument.Edge("finish", "wait", "exec", "counter", "exec"));
        var runtime = new GraphRuntime(controller(helper), true);
        runtime.replaceSimulationGraph(null, graph);
        runtime.tickSimulation(graph, null);
        var replacement = graph.copy();
        replacement.nodes().removeIf(node -> node.id().equals("wait"));
        replacement.nodes().add(node("wait", "count"));
        runtime.replaceSimulationGraph(graph, replacement);
        helper.runAtTickTime(25, () -> {
            runtime.tickSimulation(replacement, null);
            helper.assertTrue(runtime.liveOutput("counter", "value").asNumber() == 0,
                    "An old delay executed its replacement node");
            runtime.clear();
            helper.succeed();
        });
    }

    // Profiling records actual compiled calls while preview reads and closed observers stay free of samples
    @GameTest(template = "acc_presentation_empty", timeoutTicks = 20)
    public static void compiledProfilingIsOptInAndExcludesPreview(GameTestHelper helper){
        var runtime = new GraphRuntime(controller(helper), true);
        var graph = counterGraph();
        try{
            runtime.tick(graph, false);
            helper.assertTrue(runtime.graphProfile().nodes().isEmpty(), "Disabled profiling allocated node metrics");
            runtime.retainGraphProfiling();
            runtime.retainGraphProfiling();
            runtime.tick(graph, false);
            var profile = runtime.graphProfile();
            helper.assertTrue(profile.ticks() == 1 && profile.nodes().get("counter").calls() > 0
                    && profile.nodes().get("tick").calls() > 0, "Compiled execution did not record node calls");
            double val = runtime.liveOutput("counter", "value").asNumber();
            runtime.sampleRuntimePorts(null);
            runtime.previewOutput(graph, graph.nodes().get(1), "value");
            helper.assertTrue(profile.equals(runtime.graphProfile()), "Editor port sampling polluted execution metrics");
            helper.assertTrue(runtime.liveOutput("counter", "value").asNumber() == val, "Profiling changed the counter");
            runtime.releaseGraphProfiling();
            runtime.tick(graph, false);
            helper.assertTrue(runtime.graphProfile().ticks() == 2, "One editor closed another editor's profiler");
            runtime.releaseGraphProfiling();
            runtime.tick(graph, false);
            helper.assertTrue(runtime.graphProfile().nodes().isEmpty(), "The last editor left profiling active");
        }finally{ runtime.clear(); }
        helper.succeed();
    }

    // Recompilation replaces timing indices rather than attributing old work to replacement nodes
    @GameTest(template = "acc_presentation_empty", timeoutTicks = 20)
    public static void profilingResetsWithCompiledInstructions(GameTestHelper helper){
        var runtime = new GraphRuntime(controller(helper), true);
        var graph = counterGraph();
        runtime.retainGraphProfiling();
        try{
            runtime.tick(graph, false);
            var replacement = graph.copy();
            replacement.nodes().removeIf(node -> node.id().equals("counter"));
            replacement.nodes().add(node("replacement", "count"));
            replacement.edges().clear();
            replacement.edges().add(new AdvancedGraphDocument.Edge("new", "tick", "exec", "replacement", "exec"));
            runtime.tick(replacement, false);
            var profile = runtime.graphProfile();
            helper.assertTrue(profile.ticks() == 1 && !profile.nodes().containsKey("counter")
                    && profile.nodes().get("replacement").calls() > 0, "Recompilation retained stale node timings");
        }finally{ runtime.releaseGraphProfiling(); runtime.clear(); }
        helper.succeed();
    }

    // Simulated profiling forks the compiled engine and restores applied observation when closed
    @GameTest(template = "acc_presentation_empty", timeoutTicks = 30)
    public static void simulatedProfilingDoesNotStepTheAppliedGraph(GameTestHelper helper){
        var controller = controller(helper);
        var graph = counterGraph();
        helper.assertTrue(controller.saveDraft(graph, controller.getDraftGraph().revision()) && controller.applyDraft(),
                "The applied profiling fixture was rejected");
        helper.runAtTickTime(4, () -> {
            var applied = controller.graphRuntimeForDraftSession();
            double actual = applied.liveOutput("counter", "value").asNumber();
            var session = new AdvancedGraphDraftSession(controller, UUID.randomUUID());
            try{
                helper.assertTrue(session.edit(-1, 0, true, controller.getActiveGraph().toTag()), "Matching draft was rejected");
                session.profiling(true);
                session.tick(helper.getLevel().getGameTime());
                helper.assertTrue(session.profile().ticks() == 1 && session.profile().nodes().get("counter").calls() > 0,
                        "Simulated mode did not profile its independent compiled runtime");
                helper.assertTrue(applied.liveOutput("counter", "value").asNumber() == actual,
                        "Simulation stepped the applied counter");
                session.profiling(false);
                helper.assertTrue(session.profile().nodes().isEmpty(), "Closing simulated mode retained its timing arrays");
                helper.assertTrue(counter(session.changes()) == actual, "Closing simulated mode did not restore applied observation");
            }finally{ session.close(); }
            helper.succeed();
        });
    }

    // Compare the collector with an independent clock around the production compiled engine
    @GameTest(template = "acc_presentation_empty", timeoutTicks = 30)
    public static void graphProfilerAgreesWithIndependentElapsedTime(GameTestHelper helper){
        String output = System.getProperty("gadgetsngizmos.graphProfilerTiming");
        if(output == null){ helper.succeed(); return; }
        var controller = controller(helper);
        var res = new StringBuilder("mode,profiled,outer_ns_per_tick,collector_ns_per_tick,node_self_ns_per_tick\n");
        for(boolean simulated : new boolean[]{false, true}){
            var graph = counterGraph();
            var constant = node("constant", "constant_number"); constant.data().putDouble("Value", 2);
            graph.nodes().add(constant);
            String source = "counter";
            for(int idx = 0; idx < 31; idx++){
                String id = "add" + idx;
                graph.nodes().add(node(id, "add"));
                graph.edges().add(new AdvancedGraphDocument.Edge("a" + idx, source, "value", id, "a"));
                graph.edges().add(new AdvancedGraphDocument.Edge("b" + idx, "constant", "value", id, "b"));
                source = id;
            }
            graph.nodes().add(node("list", "list_create"));
            graph.nodes().add(node("map", "map_create"));
            var setter = node("setter", "variable_set"); setter.data().putString("Variable", "bench"); graph.nodes().add(setter);
            graph.variables().put("bench", AdvancedGraphDocument.Value.map(new CompoundTag()));
            graph.edges().add(new AdvancedGraphDocument.Edge("values", source, "value", "list", "value_1"));
            graph.edges().add(new AdvancedGraphDocument.Edge("fields", "list", "list", "map", "field_1"));
            graph.edges().add(new AdvancedGraphDocument.Edge("result", "map", "map", "setter", "value"));
            graph.edges().add(new AdvancedGraphDocument.Edge("set", "counter", "exec", "setter", "exec"));
            var runtime = new GraphRuntime(controller, simulated);
            try{
                runtime.compile(graph);
                runtime.retainRuntimeObservation(); runtime.runtimeChanges(null, 0);
                for(int idx = 0; idx < 2000; idx++) runtime.tick(graph, false);
                for(int round = 0; round < 4; round++){
                    for(boolean profiled : new boolean[]{false, true}){
                        if(profiled) runtime.retainGraphProfiling();
                        long start = System.nanoTime();
                        for(int idx = 0; idx < 1000; idx++) runtime.tick(graph, false);
                        long elapsed = System.nanoTime() - start;
                        var profile = runtime.graphProfile();
                        if(profiled){
                            helper.assertTrue(profile.ticks() == 1000 && profile.totalNanos() <= elapsed,
                                    "The collector exceeded the independent elapsed time");
                            helper.assertTrue(profile.nodes().get("add30").calls() > 0 && profile.nodes().get("map").calls() > 0,
                                    "The timing fixture did not execute its compiled data instructions");
                        }
                        long self = profile.nodes().values().stream().mapToLong(val -> val.selfNanos()).sum();
                        res.append(simulated ? "simulated" : "runtime").append(',').append(profiled).append(',')
                                .append(elapsed / 1000).append(',').append(profiled ? profile.totalNanos() / 1000 : 0)
                                .append(',').append(self / 1000).append('\n');
                        if(profiled) runtime.releaseGraphProfiling();
                    }
                }
            }finally{ runtime.releaseRuntimeObservation(); runtime.clear(); }
        }
        try{ java.nio.file.Files.writeString(java.nio.file.Path.of(output), res); }
        catch(java.io.IOException err){ throw new java.io.UncheckedIOException(err); }
        helper.succeed();
    }

    private static double counter(GraphRuntimeJournal.Update<AdvancedGraphDocument.Value> update){
        return update.events().stream().filter(evt -> evt.channel() == GraphRuntimeJournal.Channel.OUTPUT
                && evt.key().equals("counter:value") && evt.value() != null)
                .mapToDouble(evt -> evt.value().asNumber()).reduce((prev, next) -> next).orElse(-1);
    }

    private static AdvancedGraphDocument counterGraph(){
        var graph = new AdvancedGraphDocument();
        graph.nodes().add(node("tick", "event_tick"));
        graph.nodes().add(node("counter", "count"));
        graph.edges().add(new AdvancedGraphDocument.Edge("pulse", "tick", "exec", "counter", "exec"));
        return graph;
    }

    private static AdvancedGraphDocument.Node node(String id, String type){
        return new AdvancedGraphDocument.Node(id, type, id, 0, 0,
                AdvancedGraphNodeFactory.createDefaultData(type, AdvancedGraphNodeFactory.Context.EMPTY));
    }

    private record WirelessSource(BlockPos pos, ItemStack first) implements IRedstoneLinkable, DistanceIndependentLink{
        @Override public int getTransmittedStrength(){ return 9; }
        @Override public void setReceivedStrength(int signal){}
        @Override public boolean isListening(){ return false; }
        @Override public boolean isAlive(){ return true; }
        @Override public Couple<RedstoneLinkNetworkHandler.Frequency> getNetworkKey(){
            return Couple.create(RedstoneLinkNetworkHandler.Frequency.of(first), RedstoneLinkNetworkHandler.Frequency.of(ItemStack.EMPTY));
        }
        @Override public BlockPos getLocation(){ return pos; }
    }

    private static AdvancedContraptionControllerBlockEntity controller(GameTestHelper helper){
        helper.setBlock(new BlockPos(2, 1, 2), CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER.get().defaultBlockState());
        return (AdvancedContraptionControllerBlockEntity)helper.getLevel().getBlockEntity(
                helper.absolutePos(new BlockPos(2, 1, 2)));
    }
}
