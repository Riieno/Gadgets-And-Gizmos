package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphNodeFactory;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphLiveValue;
import com.rieno.gadgetsandgizmos.content.advanced.GraphRuntime;
import com.rieno.gadgetsandgizmos.lib.graph.GraphValueSelection;
import com.rieno.gadgetsandgizmos.lib.util.ValueProjection;
import com.rieno.gadgetsandgizmos.lib.physics.PropulsionLight;
import com.rieno.gadgetsandgizmos.config.CTConfigs;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import com.rieno.gadgetsandgizmos.neoforge.network.AdvancedControllerRuntimeDeltaPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.AdvancedControllerRuntimePayload;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// Verify presentation caches against live controllers and the supplied flight graph
@GameTestHolder("createthrusters")
@PrefixGameTestTemplate(false)
public final class AccPresentationGameTests{
    // Retire old saved light without replacing block entities or disturbing their facing
    @GameTest(template = "acc_presentation_empty", timeoutTicks = 80)
    public static void thrustersDoNotEmitLightAndClearLegacyStates(GameTestHelper helper){
        var blocks = java.util.List.of(CTBlocks.THRUSTER.get(), CTBlocks.CELEBRATION_THRUSTER.get(),
                CTBlocks.RCS_THRUSTER.get(), CTBlocks.CREATIVE_RCS_THRUSTER.get());
        var entities = new java.util.ArrayList<net.minecraft.world.level.block.entity.BlockEntity>();
        for(int idx = 0; idx < blocks.size(); idx++){
            BlockPos pos = new BlockPos(1 + idx, 2, 1);
            var state = blocks.get(idx).defaultBlockState()
                    .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, net.minecraft.core.Direction.EAST)
                    .setValue(PropulsionLight.LIGHT_LEVEL, 15);
            helper.setBlock(pos, state);
            entities.add(helper.getLevel().getBlockEntity(helper.absolutePos(pos)));
            helper.assertTrue(state.getLightEmission(helper.getLevel(), helper.absolutePos(pos)) == 0,
                    "A thruster still emits light from a legacy state");
            helper.assertTrue(!state.getBlock().hasDynamicLightEmission(state), "A thruster still requests dynamic light sampling");
        }
        helper.runAfterDelay(5, () -> {
            for(int idx = 0; idx < blocks.size(); idx++){
                BlockPos pos = helper.absolutePos(new BlockPos(1 + idx, 2, 1));
                var state = helper.getLevel().getBlockState(pos);
                helper.assertTrue(state.getValue(PropulsionLight.LIGHT_LEVEL) == 0, "A saved light level was not cleared on load");
                helper.assertTrue(state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING)
                        == net.minecraft.core.Direction.EAST, "Retiring light changed the thruster facing");
                helper.assertTrue(helper.getLevel().getBlockEntity(pos) == entities.get(idx), "Retiring light replaced a block entity");
                var manager = helper.getLevel().getAuxLightManager(pos);
                if(manager != null){
                    manager.setLightAt(pos, 14);
                    PropulsionLight.clear(helper.getLevel(), pos);
                    helper.assertTrue(manager.getLightAt(pos) == 0, "Retiring light left an auxiliary source");
                }
            }
            helper.succeed();
        });
    }

    // Keep visual plume settings available while retiring only their world-lighting switches
    @GameTest(template = "acc_presentation_empty", timeoutTicks = 20)
    public static void thrusterLightingConfigOptionsAreRemoved(GameTestHelper helper){
        var spec = CTConfigs.CLIENT_SPEC.getSpec();
        helper.assertTrue(!spec.contains("client.thrustersEmitLight") && !spec.contains("client.plumesEmitLight"),
                "A removed thruster lighting option remains in the config spec");
        helper.assertTrue(spec.contains("client.useThrusterPlumeV2Renderer") && spec.contains("client.usePlumeMetaballRendering"),
                "Removing lighting also removed plume visual settings");
        var previous = com.electronwill.nightconfig.core.CommentedConfig.inMemory();
        previous.set("client.thrustersEmitLight", true);
        previous.set("client.plumesEmitLight", true);
        previous.set("client.usePlumeMetaballRendering", false);
        CTConfigs.CLIENT_SPEC.correct(previous);
        helper.assertTrue(!previous.contains("client.thrustersEmitLight") && !previous.contains("client.plumesEmitLight"),
                "Loading an old config retained retired lighting settings");
        helper.assertTrue(Boolean.FALSE.equals(previous.get("client.usePlumeMetaballRendering")),
                "Retiring old config entries changed a plume visual setting");
        helper.succeed();
    }

    // Preserve delta publication and explicit removals for the existing update entry point
    @GameTest(template = "acc_presentation_empty", timeoutTicks = 80)
    public static void legacyUpdatesPreserveValueDeltasAndRemovals(GameTestHelper helper){
        placeBlocks(helper);
        helper.runAfterDelay(2, () -> {
            Setup setup = installGraph(helper, false);
            setup.first().receiveControllerUpdate(setup.controller(),
                    Map.of("widget_first:value", AdvancedGraphDocument.Value.number(1)), Map.of());
            setup.first().getUpdatePacket();
            setup.first().receiveControllerUpdate(setup.controller(),
                    Map.of("widget_first:value", AdvancedGraphDocument.Value.number(2),
                            "widget_first:removed", AdvancedGraphDocument.Value.number(7)), Map.of());
            CompoundTag added = setup.first().getUpdatePacket().getTag().getCompound("AccDisplayFrame");
            helper.assertTrue(added.getBoolean("ValueDelta") && !added.contains("Graph"),
                    "The legacy update entry point stopped publishing value deltas");
            setup.first().receiveControllerUpdate(setup.controller(),
                    Map.of("widget_first:value", AdvancedGraphDocument.Value.number(3)),
                    Map.of("widget_first:value", AdvancedGraphDocument.Value.number(4)));
            CompoundTag removed = setup.first().getUpdatePacket().getTag().getCompound("AccDisplayFrame");
            helper.assertTrue(removed.getList("RemovedValues", Tag.TAG_STRING).stream()
                    .anyMatch(tag -> "widget_first:removed".equals(tag.getAsString())),
                    "The legacy update entry point retained a removed value");
            helper.assertTrue(setup.first().value("widget_first", "value").asNumber() == 4,
                    "Input values overwrote authoritative output values");
            setup.first().receiveControllerUpdate(setup.controller(),
                    Map.of("widget_first:value", AdvancedGraphDocument.Value.number(5),
                            "widget_first:visible", AdvancedGraphDocument.Value.bool(false)), Map.of());
            helper.assertTrue(!setup.first().value("widget_first", "visible").asBoolean(),
                    "Hiding the last visible widget discarded its visibility value");
            setup.first().getUpdatePacket();
            setup.first().receiveControllerUpdate(setup.controller(),
                    Map.of("widget_first:value", AdvancedGraphDocument.Value.number(6),
                            "widget_first:visible", AdvancedGraphDocument.Value.bool(true)), Map.of());
            helper.assertTrue(setup.first().value("widget_first", "visible").asBoolean(),
                    "Showing a hidden widget retained stale visibility");
            helper.succeed();
        });
    }

    // Round-trip typed values and removal keys through the optional HUD network envelope
    @GameTest(template = "acc_presentation_empty", timeoutTicks = 80)
    public static void runtimeDeltaCodecPreservesTypedValues(GameTestHelper helper){
        CompoundTag nested = new CompoundTag();
        nested.put("speed", AdvancedGraphDocument.Value.number(23).toTag());
        nested.put("name", AdvancedGraphDocument.Value.string("Flight HUD").toTag());
        var value = AdvancedGraphLiveValue.from(AdvancedGraphDocument.Value.map(nested));
        var update = new AdvancedControllerRuntimePayload(BlockPos.ZERO, null, -1, Map.of(),
                Map.of("widget:value", value), Map.of(), Map.of(), Map.of());
        var packet = new AdvancedControllerRuntimeDeltaPayload(update, UUID.randomUUID(), 7, 8, false,
                Set.of("widget:removed"), Set.of("widget:old_output"));
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try{
            AdvancedControllerRuntimeDeltaPayload.STREAM_CODEC.encode(buffer, packet);
            var decoded = AdvancedControllerRuntimeDeltaPayload.STREAM_CODEC.decode(buffer);
            helper.assertTrue(buffer.readableBytes() == 0, "The HUD envelope left unread bytes");
            helper.assertTrue(decoded.streamId().equals(packet.streamId()) && decoded.baseSequence() == 7
                    && decoded.sequence() == 8 && !decoded.full(), "The HUD envelope lost its stream baseline");
            helper.assertTrue(decoded.removedInputs().equals(packet.removedInputs())
                    && decoded.removedOutputs().equals(packet.removedOutputs()), "The HUD envelope lost removals");
            helper.assertTrue(decoded.update().liveInputs().get("widget:value").value().equals(value.value()),
                    "The HUD envelope changed its structured value payload");
        }finally{
            buffer.release();
        }
        helper.succeed();
    }

    // Keep a static widget unchanged while unrelated execution continues to advance
    @GameTest(template = "acc_presentation_empty", timeoutTicks = 80)
    public static void unrelatedExecutionPreservesWidgetFrame(GameTestHelper helper){
        placeBlocks(helper);
        helper.runAfterDelay(2, () -> {
            Setup setup = installGraph(helper, false);
            GraphRuntime runtime = setup.controller().displayRuntime();
            var graph = setup.controller().activeGraphView();
            runtime.tick(graph, false);
            setup.first().receiveControllerChanges(setup.controller());
            CompoundTag frame = setup.first().displayFrame();
            double count = runtime.liveOutput("counter", "value").asNumber();
            for(int idx = 0; idx < 10; idx++){
                runtime.tick(graph, false);
                setup.first().receiveControllerChanges(setup.controller());
                helper.assertTrue(setup.first().displayFrame() == frame,
                        "Unrelated counter execution rebuilt the widget frame");
            }
            helper.assertTrue(runtime.liveOutput("counter", "value").asNumber() == count + 10,
                    "Presentation publication changed counter execution");
            helper.assertTrue(setup.first().value("widget_first", "value").asNumber() == 42,
                    "The static widget lost its input value");
            helper.succeed();
        });
    }

    // Keep separate display baselines and send changed widget ports without graph copies
    @GameTest(template = "acc_presentation_empty", timeoutTicks = 80)
    public static void displaysPublishIndependentValueDeltas(GameTestHelper helper){
        placeBlocks(helper);
        helper.runAfterDelay(2, () -> {
            Setup setup = installGraph(helper, true);
            GraphRuntime runtime = setup.controller().displayRuntime();
            runtime.tick(setup.controller().activeGraphView(), false);
            setup.first().receiveControllerChanges(setup.controller());
            setup.second().receiveControllerChanges(setup.controller());
            CompoundTag baseline = setup.first().getUpdatePacket().getTag().getCompound("AccDisplayFrame");
            helper.assertTrue(baseline.contains("Graph"), "The initial display update omitted its graph");
            setup.second().getUpdatePacket();
            runtime.tick(setup.controller().activeGraphView(), false);
            setup.first().receiveControllerChanges(setup.controller());
            setup.second().receiveControllerChanges(setup.controller());
            CompoundTag first = setup.first().getUpdatePacket().getTag().getCompound("AccDisplayFrame");
            CompoundTag second = setup.second().getUpdatePacket().getTag().getCompound("AccDisplayFrame");
            for(CompoundTag packet : new CompoundTag[]{first, second}){
                helper.assertTrue(packet.getBoolean("ValueDelta"), "A display did not publish a value delta");
                helper.assertTrue(!packet.contains("Graph"), "A value-only update copied the full graph");
                helper.assertTrue(!packet.getCompound("Values").contains("counter:value"),
                        "A widget update included an unrelated execution output");
            }
            helper.assertTrue(first.getCompound("Values").contains("widget_first:value"),
                    "The first display lost its changed value");
            helper.assertTrue(second.getCompound("Values").contains("widget_second:value"),
                    "The second display lost changes consumed by the first display");
            helper.succeed();
        });
    }

    // Require a full baseline after graph replacement and preserve detached runtime snapshots
    @GameTest(template = "acc_presentation_empty", timeoutTicks = 80)
    public static void graphReplacementResetsPresentationBaselines(GameTestHelper helper){
        placeBlocks(helper);
        helper.runAfterDelay(2, () -> {
            Setup setup = installGraph(helper, true);
            GraphRuntime runtime = setup.controller().displayRuntime();
            runtime.tick(setup.controller().activeGraphView(), false);
            setup.first().receiveControllerChanges(setup.controller());
            setup.first().getUpdatePacket();
            Map<String, AdvancedGraphDocument.Value> snapshot = runtime.liveInputs();
            double value = snapshot.get("widget_first:value").asNumber();
            runtime.tick(setup.controller().activeGraphView(), false);
            helper.assertTrue(snapshot.get("widget_first:value").asNumber() == value,
                    "A detached public snapshot changed after execution");
            var graph = setup.controller().getDraftGraph();
            graph.nodes().removeIf(node -> "static".equals(node.id()));
            applyGraph(helper, setup.controller(), graph);
            runtime.tick(setup.controller().activeGraphView(), false);
            setup.first().receiveControllerChanges(setup.controller());
            CompoundTag packet = setup.first().getUpdatePacket().getTag().getCompound("AccDisplayFrame");
            helper.assertTrue(packet.contains("Graph") && !packet.getBoolean("ValueDelta"),
                    "Graph replacement reused a stale incremental baseline");
            helper.succeed();
        });
    }

    // Compare independent projection cadences with authoritative values from the supplied graph
    @GameTest(template = "acc_presentation_empty", timeoutTicks = 80)
    public static void flightGraphProjectionsMatchRuntime(GameTestHelper helper) throws Exception{
        String graphPath = System.getProperty("acc.presentation.graph", "");
        if(graphPath.isBlank()){
            helper.succeed();
            return;
        }
        var root = JsonParser.parseString(Files.readString(Path.of(graphPath))).getAsJsonObject();
        var json = root.getAsJsonArray("storedGraphs").get(0).getAsJsonObject().get("Graph");
        Tag tag = JsonOps.INSTANCE.convertTo(NbtOps.INSTANCE, json);
        var graph = AdvancedGraphDocument.fromTag((CompoundTag)tag);
        GraphRuntime runtime = new GraphRuntime(null, true);
        Set<String> nodeIds = graph.nodes().stream().filter(node -> node.type().contains("hud_element")
                || "acc_hologram_widget".equals(node.type())).map(AdvancedGraphDocument.Node::id)
                .collect(java.util.stream.Collectors.toSet());
        GraphValueSelection selection = new GraphValueSelection(nodeIds);
        ValueProjection<String, AdvancedGraphDocument.Value> fast = new ValueProjection<>(selection);
        ValueProjection<String, AdvancedGraphDocument.Value> slow = new ValueProjection<>(selection);
        Map<String, AdvancedGraphDocument.Value> fastClient = new HashMap<>();
        Map<String, AdvancedGraphDocument.Value> slowClient = new HashMap<>();
        for(int idx = 0; idx < 20; idx++){
            runtime.tick(graph, false);
            fast.update(runtime, runtime.inputChangesSince(fast.revision()), runtime.liveInputsView()).applyTo(fastClient);
            Map<String, AdvancedGraphDocument.Value> expected = new HashMap<>(runtime.liveInputs());
            expected.keySet().removeIf(key -> !selection.test(key));
            helper.assertTrue(expected.equals(fastClient), "Flight HUD projection diverged from runtime inputs");
            if(idx % 7 == 0){
                slow.update(runtime, runtime.inputChangesSince(slow.revision()), runtime.liveInputsView()).applyTo(slowClient);
                helper.assertTrue(expected.equals(slowClient), "A slower flight HUD reader lost changes");
            }
        }
        helper.succeed();
    }

    // Place separate display roots beside a controller in the isolated test world
    private static void placeBlocks(GameTestHelper helper){
        helper.setBlock(new BlockPos(1, 1, 1), CTBlocks.ACC_DISPLAY.get().defaultBlockState());
        helper.setBlock(new BlockPos(3, 1, 1), CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER.get().defaultBlockState());
        helper.setBlock(new BlockPos(5, 1, 1), CTBlocks.ACC_DISPLAY.get().defaultBlockState());
    }

    // Build a counter graph with either a static or an execution-driven widget input
    private static Setup installGraph(GameTestHelper helper, boolean dynamic){
        var controller = (AdvancedContraptionControllerBlockEntity)helper.getLevel()
                .getBlockEntity(helper.absolutePos(new BlockPos(3, 1, 1)));
        var first = (AccDisplayBlockEntity)helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(1, 1, 1)));
        var second = (AccDisplayBlockEntity)helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(5, 1, 1)));
        var graph = controller.getDraftGraph();
        graph.nodes().clear();
        graph.edges().clear();
        graph.nodes().add(node("tick", "event_tick"));
        graph.nodes().add(node("counter", "count"));
        var constant = node("static", "add");
        CompoundTag defaults = constant.data().getCompound("Defaults");
        defaults.put("a", AdvancedGraphDocument.Value.number(42).toTag());
        defaults.put("b", AdvancedGraphDocument.Value.number(0).toTag());
        constant.data().put("Defaults", defaults);
        graph.nodes().add(constant);
        graph.nodes().add(widget("widget_first", first));
        graph.nodes().add(widget("widget_second", second));
        graph.edges().add(new AdvancedGraphDocument.Edge("execute", "tick", "exec", "counter", "exec"));
        String source = dynamic ? "counter" : "static";
        graph.edges().add(new AdvancedGraphDocument.Edge("first_value", source, "value", "widget_first", "value"));
        graph.edges().add(new AdvancedGraphDocument.Edge("second_value", source, "value", "widget_second", "value"));
        applyGraph(helper, controller, graph);
        return new Setup(controller, first, second);
    }

    // Preserve the same validation and application path used by the editor
    private static void applyGraph(GameTestHelper helper, AdvancedContraptionControllerBlockEntity controller,
                                   AdvancedGraphDocument graph){
        helper.assertTrue(controller.saveDraft(graph, controller.getDraftGraph().revision()), "Could not save the test graph");
        helper.assertTrue(controller.applyDraft(), "Could not apply the test graph: " + controller.getGraphDiagnostics());
    }

    // Build production defaults for a test node
    private static AdvancedGraphDocument.Node node(String id, String type){
        return new AdvancedGraphDocument.Node(id, type, id, 0, 0,
                AdvancedGraphNodeFactory.createDefaultData(type, AdvancedGraphNodeFactory.Context.EMPTY));
    }

    // Bind a widget to one concrete display root
    private static AdvancedGraphDocument.Node widget(String id, AccDisplayBlockEntity display){
        var node = node(id, "acc_display_widget");
        CompoundTag target = new CompoundTag();
        target.putLong("BlockPos", display.getBlockPos().asLong());
        target.putString("BlockId", "createthrusters:acc_display");
        node.data().put("TargetData", target);
        return node;
    }

    // Retain the block entities belonging to one independent test structure
    private record Setup(AdvancedContraptionControllerBlockEntity controller,
                         AccDisplayBlockEntity first, AccDisplayBlockEntity second){}
}
