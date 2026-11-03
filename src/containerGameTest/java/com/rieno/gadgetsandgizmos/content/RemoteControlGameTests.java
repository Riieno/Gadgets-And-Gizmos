package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.authlib.GameProfile;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphNodeFactory;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphCatalog;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphEditingService;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedHudInteractions;
import com.rieno.gadgetsandgizmos.content.advanced.GraphRuntime;
import com.rieno.gadgetsandgizmos.config.CTConfigs;
import com.rieno.gadgetsandgizmos.lib.chat.ChatPlayers;
import com.rieno.gadgetsandgizmos.lib.chat.ServerChatApi;
import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;
import com.rieno.gadgetsandgizmos.lib.graph.PlayerGraphValue;
import com.rieno.gadgetsandgizmos.lib.graph.GraphRuntimeJournal;
import com.rieno.gadgetsandgizmos.lib.graph.edit.PrivilegedGraphEdits;
import com.rieno.gadgetsandgizmos.lib.physics.SableSubLevelLifecycleApi;
import com.rieno.gadgetsandgizmos.lib.physics.SableTransformApi;
import com.rieno.gadgetsandgizmos.lib.view.RemoteViewSessions;
import com.rieno.gadgetsandgizmos.lib.view.ViewBodyAnchor;
import com.rieno.gadgetsandgizmos.lib.view.ViewReference;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

// Exercise remote body frames, native camera interactions and privileged ACC execution
@GameTestHolder("createthrusters_remote_controls")
@PrefixGameTestTemplate(false)
public final class RemoteControlGameTests{
/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        FUNCTIONS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    // Follow translation and rotation of the player's original SubLevel
    @GameTest(template = "empty")
    public static void bodyAnchorFollowsItsOriginalFrame(GameTestHelper helper){
        var body = ShipDoorGameTests.newBody(helper);
        var entity = EntityType.ARMOR_STAND.create(helper.getLevel());
        try{
            Vec3 local = body.getPlot().getCenterBlock().getCenter().add(2, 1, 0);
            entity.setPos(SableTransformApi.toWorldPosition(body, local));
            ((EntityMovementExtension)entity).sable$setTrackingSubLevel(body);
            var anchor = new ViewBodyAnchor(entity);
            body.logicalPose().position().add(10, 5, -3);
            body.logicalPose().orientation().rotateY(Math.PI / 2);
            helper.assertTrue(anchor.position().distanceToSqr(SableTransformApi.toWorldPosition(body, local)) < 1.0E-8,
                    "The body anchor retained the activation world coordinates");
        }finally{ SableSubLevelLifecycleApi.remove(helper.getLevel(), List.of(body)); }
        helper.succeed();
    }

    // Enter and leave remote view without dismounting or teleporting the seated body
    @GameTest(template = "empty")
    public static void seatedPlayersCanControlCameras(GameTestHelper helper){
        var camera = camera(helper);
        var player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "SeatedCamera"), ClientInformation.createDefault());
        player.connection = operator(helper).connection;
        var seat = EntityType.BOAT.create(helper.getLevel());
        seat.setPos(helper.absolutePos(new BlockPos(1, 2, 1)).getCenter());
        helper.assertTrue(player.startRiding(seat, true), "The fixture could not seat its player");
        try{
            helper.assertTrue(RemoteViewSessions.open(player, ViewReference.of(camera)), "A seated player could not open the camera");
            helper.assertTrue(player.getVehicle() == seat, "Opening the camera dismounted its player");
            RemoteViewSessions.close(player);
            helper.assertTrue(player.getVehicle() == seat && !RemoteViewSessions.isLocked(player), "Exiting lost the seat or body controls");
        }finally{ RemoteViewSessions.close(player); player.stopRiding(); seat.discard(); }
        helper.succeed();
    }

    // Block held-item placement while preserving an existing lever's native use
    @GameTest(template = "empty")
    public static void cameraUseCannotPlaceOrBreakBlocks(GameTestHelper helper){
        var camera = camera(helper);
        var player = operator(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COBBLESTONE));
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        helper.getLevel().setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        try{
            helper.assertTrue(RemoteViewSessions.open(player, ViewReference.of(camera)), "Camera control did not open");
            player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND,
                    new BlockHitResult(pos.getCenter().add(0, 0.5, 0), Direction.UP, pos, false));
            helper.assertTrue(helper.getLevel().getBlockState(pos.above()).isAir(), "Camera use placed a held block");
            helper.assertTrue(!player.gameMode.destroyBlock(pos), "Camera use destroyed a block");
            BlockPos lever = pos.above();
            helper.getLevel().setBlockAndUpdate(lever, Blocks.LEVER.defaultBlockState());
            player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND,
                    new BlockHitResult(lever.getCenter(), Direction.UP, lever, false));
            helper.assertTrue(helper.getLevel().getBlockState(lever).getValue(LeverBlock.POWERED), "Camera restrictions blocked the existing lever");
        }finally{ RemoteViewSessions.close(player); }
        helper.succeed();
    }

    // Require an operator to install or alter a Command node while retaining normal execution
    @GameTest(template = "empty")
    public static void commandNodesRequireOperatorEditsAndCaptureResults(GameTestHelper helper){
        helper.setBlock(new BlockPos(1, 1, 1), CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER.get());
        var controller = (AdvancedContraptionControllerBlockEntity)helper.getBlockEntity(new BlockPos(1, 1, 1));
        var graph = commandGraph("time query gametime");
        helper.assertTrue(!controller.saveDraft(graph.copy(), controller.getDraftGraph().revision()), "An unauthenticated caller installed a Command node");
        PrivilegedGraphEdits.run(operator(helper), () -> {
            helper.assertTrue(controller.saveDraft(graph.copy(), controller.getDraftGraph().revision()), "An operator could not install the Command node");
            helper.assertTrue(controller.applyDraft(), "The command graph failed validation");
        });
        var replacement = controller.getDraftGraph();
        replacement.nodes().clear(); replacement.edges().clear();
        helper.assertTrue(!controller.saveDraft(replacement, replacement.revision()), "A non-operator deleted a Command node");
        var runtime = new GraphRuntime(controller);
        try{
            runtime.tick(controller.getActiveGraph());
            helper.assertTrue(runtime.liveOutput("command", "success").asBoolean(), "The applied command did not succeed");
            helper.assertTrue(!runtime.liveOutput("command", "output").asString().isBlank(), "Command feedback was not captured");
        }finally{ runtime.clear(); }
        helper.succeed();
    }

    // Leave unsaved commands inert and expose syntax errors as failed command results
    @GameTest(template = "empty")
    public static void draftCommandsStayInertAndInvalidCommandsFail(GameTestHelper helper){
        helper.setBlock(new BlockPos(1, 1, 1), CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER.get());
        var controller = (AdvancedContraptionControllerBlockEntity)helper.getBlockEntity(new BlockPos(1, 1, 1));
        var preview = new GraphRuntime(controller, true);
        var runtime = new GraphRuntime(controller);
        try{
            preview.tickSimulation(commandGraph("time set midnight"), null);
            helper.assertTrue(!preview.liveOutput("command", "success").asBoolean(), "An unsaved command executed");
            runtime.tick(commandGraph("this_command_does_not_exist"));
            helper.assertTrue(!runtime.liveOutput("command", "success").asBoolean(), "An invalid command reported success");
            helper.assertTrue(!runtime.liveOutput("command", "output").asString().isBlank(), "Command errors were not captured");
        }finally{ preview.clear(); runtime.clear(); }
        helper.succeed();
    }

    // Evaluate wired reset settings and clear the accumulator before another movement arrives
    @GameTest(template = "empty", timeoutTicks = 30)
    public static void wiredMouseResetStartsTheNextSampleAtZero(GameTestHelper helper){
        helper.setBlock(new BlockPos(1, 1, 1), CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER.get());
        var controller = (AdvancedContraptionControllerBlockEntity)helper.getBlockEntity(new BlockPos(1, 1, 1));
        var graph = new AdvancedGraphDocument();
        var data = AdvancedGraphNodeFactory.createDefaultData("mouse_input", AdvancedGraphNodeFactory.Context.EMPTY);
        data.putString("MouseInput", "mouse_x");
        graph.nodes().add(new AdvancedGraphDocument.Node("mouse", "mouse_input", "", 200, 0, data));
        var reset = new CompoundTag(); reset.putBoolean("Value", true);
        var timeout = new CompoundTag(); timeout.putDouble("Value", 2);
        graph.nodes().add(new AdvancedGraphDocument.Node("reset", "constant_boolean", "", 0, 0, reset));
        graph.nodes().add(new AdvancedGraphDocument.Node("timeout", "constant_number", "", 0, 100, timeout));
        graph.edges().add(new AdvancedGraphDocument.Edge("reset", "reset", "value", "mouse", "reset_axis"));
        graph.edges().add(new AdvancedGraphDocument.Edge("timeout", "timeout", "value", "mouse", "timeout"));
        helper.assertTrue(controller.saveDraft(graph, controller.getDraftGraph().revision()) && controller.applyDraft(),
                "The wired mouse graph did not apply");
        controller.tick();
        controller.handleMouseInput("mouse_x", 0.75, true);
        helper.assertTrue(controller.getMouseInputValue("mouse_x") == 0.75, "The mouse sample was rejected");
        helper.runAfterDelay(3, () -> {
            controller.resetStaleMouseAxes();
            helper.assertTrue(controller.getMouseInputValue("mouse_x") == 0, "The evaluated axis timeout did not reset");
            controller.applyClientMouseInput("mouse_x", controller.getMouseInputValue("mouse_x") + 0.1, true);
            helper.assertTrue(Math.abs(controller.getMouseInputValue("mouse_x") - 0.1) < 1.0E-9,
                    "The next sample resumed the previous accumulator");
            helper.succeed();
        });
    }

    // Keep each queued actor's information intact through persistence and later player movement
    @GameTest(template = "empty")
    public static void playerSnapshotsSurviveQueuedInputsAndRestart(GameTestHelper helper){
        var controller = controller(helper);
        var graph = new AdvancedGraphDocument();
        graph.nodes().add(inputNode("interaction", "event_physical_interaction"));
        var first = operator(helper); first.setHealth(17);
        var second = operator(helper); second.setHealth(13);
        var runtime = new GraphRuntime(controller);
        var restored = new GraphRuntime(controller);
        try{
            runtime.compile(graph);
            runtime.enqueuePhysicalInteraction(first, true, false, "use");
            first.setHealth(8);
            runtime.enqueuePhysicalInteraction(second, true, true, "use");
            second.setHealth(4);
            var snapshot = runtime.createShutdownSnapshot();
            var legacy = snapshot.getList("Events", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0);
            legacy.remove("PlayerValue");
            legacy.getCompound("Data").getCompound("Payload").getCompound("player").putString("Type", "map");
            restored.compile(graph);
            restored.restoreShutdownSnapshot(snapshot);
            restored.retainRuntimeObservation();
            var baseline = restored.runtimeChanges(null, 0);
            restored.tick(graph);
            var events = restored.runtimeChanges(baseline.streamId(), baseline.sequence()).events().stream()
                    .filter(evt -> evt.channel() == GraphRuntimeJournal.Channel.OUTPUT && "interaction:player".equals(evt.key()))
                    .map(GraphRuntimeJournal.Event::value).filter(java.util.Objects::nonNull).toList();
            helper.assertTrue(events.stream().anyMatch(value -> first.getUUID().toString().equals(
                    GraphRuntime.structuredValue(value, "uuid").asString()) && GraphRuntime.structuredValue(value, "health").asNumber() == 17),
                    "The first actor's queued snapshot was lost or resampled");
            helper.assertTrue(events.stream().anyMatch(value -> second.getUUID().toString().equals(
                    GraphRuntime.structuredValue(value, "uuid").asString()) && GraphRuntime.structuredValue(value, "health").asNumber() == 13),
                    "The second actor's queued snapshot was lost or resampled");
            assertPlayer(helper, restored.liveOutput("interaction", "player"), second, 13);
        }finally{ runtime.clear(); restored.clear(); }
        helper.succeed();
    }

    // Expose the actual configured-key and mouse actors without inventing an owner for automation
    @GameTest(template = "empty")
    public static void configuredAndMouseInputsExposeTheirPlayers(GameTestHelper helper){
        var controller = controller(helper);
        var graph = new AdvancedGraphDocument();
        var key = inputNode("key", "controller_channel_input"); key.data().putString("BindingId", "test");
        var mouse = inputNode("mouse", "mouse_input"); mouse.data().putString("MouseInput", "mouse_x");
        graph.nodes().add(key); graph.nodes().add(mouse);
        var first = operator(helper); first.setHealth(17);
        var second = operator(helper); second.setHealth(13);
        var runtime = new GraphRuntime(controller);
        try{
            runtime.withPlayerInteraction(first, () -> runtime.enqueueExternal("input:test:active", first.getUUID()));
            first.setHealth(8);
            runtime.withPlayerInteraction(second, () -> runtime.enqueueExternal("mouse:mouse_x:active", second.getUUID()));
            second.setHealth(4);
            runtime.tick(graph);
            assertPlayer(helper, runtime.liveOutput("key", "player"), first, 17);
            assertPlayer(helper, runtime.liveOutput("mouse", "player"), second, 13);
            runtime.enqueueExternal("input:test:active", (UUID)null);
            runtime.tick(graph);
            helper.assertTrue(PlayerGraphValue.id(GraphRuntime.toLibraryValue(runtime.liveOutput("key", "player"))) == null,
                    "An automated input borrowed the previous player's identity");
        }finally{ runtime.clear(); }
        helper.succeed();
    }

    // Retain independent Player outputs for each HUD, display and hologram control
    @GameTest(template = "empty")
    public static void compositeWidgetsRetainEachInteractingPlayer(GameTestHelper helper){
        var controller = controller(helper);
        var first = operator(helper); first.setHealth(17);
        var second = operator(helper); second.setHealth(13);
        for(String type : List.of("advanced_hud_element", "acc_display_widget", "acc_hologram_widget")){
            var graph = new AdvancedGraphDocument();
            var widget = inputNode("widget", type);
            var elements = new net.minecraft.nbt.ListTag();
            for(String id : List.of("first", "second")){
                var elm = new CompoundTag(); elm.putString("Type", "button"); elm.putString("InteractionId", id); elements.add(elm);
            }
            widget.data().put("WidgetElements", elements);
            graph.nodes().add(widget);
            AdvancedHudInteractions.synchronize(widget, graph.edges());
            var runtime = new GraphRuntime(controller);
            try{
                runtime.withPlayerInteraction(first, () -> runtime.enqueueHudInteraction("widget", "first", AdvancedGraphDocument.Value.bool(true)));
                runtime.withPlayerInteraction(second, () -> runtime.enqueueHudInteraction("widget", "second", AdvancedGraphDocument.Value.bool(true)));
                runtime.tick(graph);
                var ports = widget.data().getList("WidgetElements", net.minecraft.nbt.Tag.TAG_COMPOUND);
                assertPlayer(helper, runtime.liveOutput("widget", ports.getCompound(0).getString("PlayerPort")), first, 17);
                assertPlayer(helper, runtime.liveOutput("widget", ports.getCompound(1).getString("PlayerPort")), second, 13);
                assertPlayer(helper, runtime.liveOutput("widget", "player"), second, 13);
            }finally{ runtime.clear(); }
        }
        helper.succeed();
    }

    // Use a Player wire as a chat audience without changing legacy list inputs
    @GameTest(template = "empty")
    public static void playerReferencesFilterChatAndReachAllChatNodeInputs(GameTestHelper helper){
        var controller = controller(helper);
        var graph = new AdvancedGraphDocument();
        var interaction = inputNode("interaction", "event_physical_interaction");
        var chat = inputNode("chat", "watch_chat");
        chat.data().getCompound("Defaults").put("start_word", AdvancedGraphDocument.Value.string("!test").toTag());
        graph.nodes().add(interaction); graph.nodes().add(chat);
        graph.edges().add(new AdvancedGraphDocument.Edge("audience", "interaction", "player", "chat", "players"));
        for(String type : List.of("watch_chat", "send_chat_message", "createthrusters:announce")){
            helper.assertTrue(AdvancedGraphCatalog.compatible("player", inputNode("target", type), "players"),
                    type + " rejected a Player audience wire");
        }
        var player = operator(helper); player.setHealth(17);
        var other = operator(helper);
        var runtime = new GraphRuntime(controller);
        try{
            runtime.enqueuePhysicalInteraction(player, true, false, "use");
            runtime.tick(graph);
            helper.assertTrue(!runtime.captureChatMessage(graph, new ServerChatApi.Message("!test blocked", other.getUUID(), other.getGameProfile().getName())).queued(),
                    "The Player filter accepted a different sender");
            var captured = runtime.withPlayerInteraction(player, () -> runtime.captureChatMessage(graph,
                    new ServerChatApi.Message("!test one two", player.getUUID(), player.getGameProfile().getName())));
            helper.assertTrue(captured.queued(), "The Player filter rejected its matching sender");
            runtime.tick(graph);
            assertPlayer(helper, runtime.liveOutput("chat", "player"), player, 17);
            var roundTrip = GraphRuntime.toLibraryValue(GraphRuntime.fromLibraryValue(GraphValue.list(List.of(PlayerGraphValue.capture(player)))));
            helper.assertTrue(ChatPlayers.identifiers(roundTrip).equals(List.of(player.getUUID().toString())),
                    "Nested Player references lost their identity during graph serialization");
        }finally{ runtime.clear(); }
        helper.succeed();
    }

    // Apply the switch to already compiled commands and reject creation even by operators
    @GameTest(template = "empty")
    public static void serverCommandSwitchGatesExistingAndNewNodes(GameTestHelper helper){
        boolean prev = CTConfigs.SERVER.enableCommandNodes.get();
        var controller = controller(helper);
        var runtime = new GraphRuntime(controller);
        BlockPos target = helper.absolutePos(new BlockPos(2, 1, 2));
        var graph = commandGraph("setblock " + target.getX() + " " + target.getY() + " " + target.getZ() + " minecraft:gold_block");
        try{
            CTConfigs.SERVER.enableCommandNodes.set(true);
            PrivilegedGraphEdits.run(operator(helper), () -> {
                helper.assertTrue(controller.saveDraft(graph.copy(), controller.getDraftGraph().revision()) && controller.applyDraft(),
                        "The enabled command graph could not be installed");
            });
            runtime.tick(graph);
            helper.assertTrue(helper.getLevel().getBlockState(target).is(Blocks.GOLD_BLOCK), "The enabled command did not execute");
            helper.getLevel().setBlockAndUpdate(target, Blocks.AIR.defaultBlockState());
            CTConfigs.SERVER.enableCommandNodes.set(false);
            runtime.tick(graph);
            helper.assertTrue(helper.getLevel().getBlockState(target).isAir(), "A compiled command executed after being disabled");
            helper.assertTrue(!runtime.liveOutput("command", "success").asBoolean(), "A disabled command retained successful execution output");
            helper.assertTrue(AdvancedGraphCatalog.get("createthrusters:command") != null
                    && AdvancedGraphCatalog.all().stream().noneMatch(node -> node.id().equals("createthrusters:command")),
                    "Disabled commands were removed from saved definitions or remained in the browser");
            helper.assertTrue(new AdvancedGraphEditingService(controller).nodeTypes().stream()
                    .noneMatch(node -> node.id().equals("createthrusters:command")), "The peripheral node library exposed disabled commands");
            var added = controller.getDraftGraph();
            added.nodes().add(new AdvancedGraphDocument.Node("new_command", "createthrusters:command", "", 400, 0,
                    graph.nodes().get(1).data()));
            PrivilegedGraphEdits.run(operator(helper), () -> helper.assertTrue(!controller.saveDraft(added, added.revision()),
                    "An operator created a command while the server switch was disabled"));
            helper.assertTrue(!controller.getDraftGraph().nodes().isEmpty(), "Disabling commands deleted saved nodes");
        }finally{ CTConfigs.SERVER.enableCommandNodes.set(prev); runtime.clear(); }
        helper.succeed();
    }

    // Create a fresh controller owned by one isolated test
    private static AdvancedContraptionControllerBlockEntity controller(GameTestHelper helper){
        helper.setBlock(new BlockPos(1, 1, 1), CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER.get());
        return (AdvancedContraptionControllerBlockEntity)helper.getBlockEntity(new BlockPos(1, 1, 1));
    }

    // Use the same defaults as the node browser
    private static AdvancedGraphDocument.Node inputNode(String id, String type){
        return new AdvancedGraphDocument.Node(id, type, "", 0, 0,
                AdvancedGraphNodeFactory.createDefaultData(type, AdvancedGraphNodeFactory.Context.EMPTY));
    }

    // Check both the stable reference and the interaction-time health snapshot
    private static void assertPlayer(GameTestHelper helper, AdvancedGraphDocument.Value value, ServerPlayer player, double health){
        helper.assertTrue(value != null && "player".equals(value.type()), "The output was not a Player value");
        helper.assertTrue(player.getUUID().toString().equals(GraphRuntime.structuredValue(value, "uuid").asString()), "The Player output named the wrong actor");
        helper.assertTrue(GraphRuntime.structuredValue(value, "health").asNumber() == health, "The Player information was resampled or belonged to another actor");
    }

    // Build a graph whose command runs from an ordinary tick pulse
    private static AdvancedGraphDocument commandGraph(String command){
        var graph = new AdvancedGraphDocument();
        graph.nodes().add(new AdvancedGraphDocument.Node("tick", "event_tick", "", 0, 0, new CompoundTag()));
        var data = AdvancedGraphNodeFactory.createDefaultData("createthrusters:command", AdvancedGraphNodeFactory.Context.EMPTY);
        data.getCompound("Defaults").put("command", AdvancedGraphDocument.Value.string(command).toTag());
        graph.nodes().add(new AdvancedGraphDocument.Node("command", "createthrusters:command", "", 180, 0, data));
        graph.edges().add(new AdvancedGraphDocument.Edge("exec", "tick", "exec", "command", "exec"));
        return graph;
    }

    // Supply a loaded library view source
    private static CameraBlockEntity camera(GameTestHelper helper){
        helper.setBlock(new BlockPos(4, 1, 4), CTBlocks.CAMERA.get());
        return (CameraBlockEntity)helper.getBlockEntity(new BlockPos(4, 1, 4));
    }

    // Supply an authenticated permission level without changing the test server's operator list
    private static FakePlayer operator(GameTestHelper helper){
        return new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "CommandOperator")){
            @Override public boolean hasPermissions(int level){ return level <= 2; }
        };
    }
}
