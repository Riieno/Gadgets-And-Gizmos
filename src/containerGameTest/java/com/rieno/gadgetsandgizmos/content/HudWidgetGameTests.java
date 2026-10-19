package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.content.advanced.*;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("createthrusters")
@PrefixGameTestTemplate(false)
public final class HudWidgetGameTests{
    private HudWidgetGameTests(){}

    @GameTest(template = "empty_container", batch = "hud_widgets")
    public static void projectedControlsCanBePickedAwayFromTheirDisplay(GameTestHelper ctx) throws Exception{
        BlockPos pos = ctx.absolutePos(new BlockPos(4, 4, 4));
        for(Direction facing : Direction.Plane.HORIZONTAL){
            var state = CTBlocks.ACC_DISPLAY_PANEL.get().defaultBlockState().setValue(AccDisplayBlock.FACING, facing);
            ctx.getLevel().setBlock(pos, state, 3);
            var display = (AccDisplayBlockEntity)ctx.getLevel().getBlockEntity(pos);
            var data = AdvancedGraphNodeFactory.createDefaultData("acc_hologram_widget", null);
            CompoundTag target = new CompoundTag(); target.putLong("BlockPos", pos.asLong()); data.put("TargetData", target);
            var defaults = data.getCompound("Defaults");
            defaults.put("offset_x", AdvancedGraphDocument.Value.number(1.2).toTag());
            defaults.put("offset_y", AdvancedGraphDocument.Value.number(0.4).toTag());
            defaults.put("offset_z", AdvancedGraphDocument.Value.number(0.75).toTag());
            defaults.put("size_x", AdvancedGraphDocument.Value.number(2).toTag());
            defaults.put("size_y", AdvancedGraphDocument.Value.number(1).toTag());
            defaults.put("projection_rotation", AdvancedGraphDocument.Value.number(45).toTag());
            CompoundTag button = new CompoundTag(); button.putString("Type", "button");
            button.putInt("X", 0); button.putInt("Y", 0); button.putInt("W", 140); button.putInt("H", 72);
            ListTag elements = new ListTag(); elements.add(button); data.put("WidgetElements", elements);
            var node = new AdvancedGraphDocument.Node("hologram", "acc_hologram_widget", "", 0, 0, data);
            var graph = new AdvancedGraphDocument(); graph.nodes().add(node); AdvancedHudInteractions.synchronize(node, graph.edges());
            CompoundTag frame = new CompoundTag(); frame.putString("State", "graph"); frame.putString("Mode", "widgets"); frame.put("Graph", graph.toTag());
            var field = AccDisplayBlockEntity.class.getDeclaredField("displayFrame"); field.setAccessible(true); field.set(display, frame);
            Vec3 right = Vec3.atLowerCornerOf(display.screenRight().getNormal());
            Vec3 outward = Vec3.atLowerCornerOf(facing.getNormal());
            double ppb = AccDisplayBlockEntity.PIXELS_PER_BLOCK;
            double plane = AccDisplayWidgetGeometry.renderPlane(AccDisplayBlock.DisplayType.PANEL, state.getValue(AccDisplayBlock.Z_ALIGNMENT));
            Vec3 center = Vec3.atCenterOf(pos).subtract(right.scale(display.surfacePixelWidth() / (2 * ppb)))
                    .add(0, display.surfacePixelHeight() / (2 * ppb), 0)
                    .add(right.scale(2.2)).add(0, -0.1, 0).add(outward.scale(-plane + 0.75));
            var hit = AccDisplayWidgetGeometry.pick(display, center.add(outward.scale(2)), outward.reverse(), 3);
            ctx.assertTrue(hit != null && hit.node().id().equals("hologram"), "Floating control was not pickable facing " + facing);
            ctx.assertTrue(Math.abs(hit.fraction() - 0.5) < 0.001, "Rotated canvas picked the wrong slider fraction");
            ctx.assertTrue(AccDisplayWidgetGeometry.pick(display, center.add(outward.scale(2)), outward.reverse(), 1) == null,
                    "Out-of-reach control was accepted");
            CompoundTag values = new CompoundTag(); values.put("hologram:visible", AdvancedGraphDocument.Value.bool(false).toTag());
            frame.put("Values", values);
            ctx.assertTrue(AccDisplayWidgetGeometry.pick(display, center.add(outward.scale(2)), outward.reverse(), 3) == null,
                    "Hidden hologram accepted an interaction");
        }
        ctx.succeed();
    }

    @GameTest(template = "empty_container", batch = "hud_widgets")
    public static void widgetChangeOutputsRunInTheNativeGraphRuntime(GameTestHelper ctx) throws Exception{
        BlockPos pos = ctx.absolutePos(new BlockPos(2, 3, 2));
        ctx.getLevel().setBlock(pos, CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER.get().defaultBlockState(), 3);
        var controller = (AdvancedContraptionControllerBlockEntity)ctx.getLevel().getBlockEntity(pos);
        var field = AdvancedContraptionControllerBlockEntity.class.getDeclaredField("graphRuntime"); field.setAccessible(true);
        var runtime = (GraphRuntime)field.get(controller);
        var graph = new AdvancedGraphDocument();
        var data = AdvancedGraphNodeFactory.createDefaultData("acc_display_widget", null);
        ListTag elements = new ListTag(); CompoundTag slider = new CompoundTag();
        slider.putString("Type", "slider"); slider.putString("InteractionId", "slider"); elements.add(slider);
        data.put("WidgetElements", elements);
        var widget = new AdvancedGraphDocument.Node("widget", "acc_display_widget", "", 0, 0, data);
        graph.nodes().add(widget); AdvancedHudInteractions.synchronize(widget, graph.edges());
        slider = widget.data().getList("WidgetElements", 10).getCompound(0);
        var count = new AdvancedGraphDocument.Node("count", "count", "", 0, 0, AdvancedGraphNodeFactory.createDefaultData("count", null));
        graph.nodes().add(count);
        graph.edges().add(new AdvancedGraphDocument.Edge("changed", "widget", slider.getString("ExecPort"), "count", "exec"));
        runtime.compile(graph); runtime.tick(graph, false);
        runtime.enqueueHudInteraction("widget", "slider", AdvancedGraphDocument.Value.number(0.25)); runtime.tick(graph, false);
        runtime.enqueueHudInteraction("widget", "slider", AdvancedGraphDocument.Value.number(0.25)); runtime.tick(graph, false);
        ctx.assertTrue(runtime.liveOutput("count", "value").asNumber() == 1, "Duplicate value pulsed again");
        runtime.enqueueHudInteraction("widget", "slider", AdvancedGraphDocument.Value.number(0.75)); runtime.tick(graph, false);
        ctx.assertTrue(runtime.liveOutput("count", "value").asNumber() == 2, "Second changed value did not pulse");
        ctx.succeed();
    }

    @GameTest(template = "empty_container", batch = "hud_widgets")
    public static void displayValueUpdatesRetainTheDecodedLayout(GameTestHelper ctx) throws Exception{
        BlockPos pos = ctx.absolutePos(new BlockPos(2, 4, 2));
        // Client packet decoding must not trigger a server world's source-frame rebuild
        var display = new AccDisplayBlockEntity(pos, CTBlocks.ACC_DISPLAY_PANEL.get().defaultBlockState());
        var empty = display.graph();
        ctx.assertTrue(empty == display.graph(), "An empty display rebuilt its graph during rendering");
        var graph = new AdvancedGraphDocument();
        graph.nodes().add(new AdvancedGraphDocument.Node("widget", "acc_display_widget", "", 0, 0,
                AdvancedGraphNodeFactory.createDefaultData("acc_display_widget", null)));
        CompoundTag frame = new CompoundTag(); frame.putString("State", "graph");
        frame.put("Graph", graph.toTag()); frame.putInt("GraphRevision", 1);
        CompoundTag packet = new CompoundTag(); packet.put("AccDisplayFrame", frame);
        display.read(packet, ctx.getLevel().registryAccess(), true);
        var retained = display.graph();
        CompoundTag delta = new CompoundTag(); delta.putString("State", "graph");
        delta.putBoolean("ValueDelta", true); delta.putInt("GraphRevision", 1);
        CompoundTag values = new CompoundTag(); values.put("widget:value", AdvancedGraphDocument.Value.number(0.75).toTag());
        delta.put("Values", values); packet.put("AccDisplayFrame", delta);
        display.read(packet, ctx.getLevel().registryAccess(), true);
        ctx.assertTrue(retained == display.graph(), "A value update rebuilt the graph layout");
        ctx.assertTrue(display.value("widget", "value").asNumber() == 0.75, "Retained layout lost its live value update");
        delta.remove("ValueDelta"); delta.putString("State", "camera"); packet.put("AccDisplayFrame", delta);
        display.read(packet, ctx.getLevel().registryAccess(), true);
        var cleared = display.graph();
        ctx.assertTrue(cleared.nodes().isEmpty() && cleared == display.graph(), "Cleared display retained stale widgets or rebuilt an empty graph");
        ctx.succeed();
    }

    @GameTest(template = "empty_container", batch = "hologram_publication")
    public static void hologramOnlyGraphsPublishTheirDisplayWithoutASurfaceWidget(GameTestHelper ctx) throws Exception{
        BlockPos controllerPos = ctx.absolutePos(new BlockPos(2, 3, 2));
        BlockPos displayPos = controllerPos.east(2);
        ctx.getLevel().setBlock(controllerPos, CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER.get().defaultBlockState(), 3);
        ctx.getLevel().setBlock(displayPos, CTBlocks.ACC_DISPLAY_PANEL.get().defaultBlockState(), 3);
        var controller = (AdvancedContraptionControllerBlockEntity)ctx.getLevel().getBlockEntity(controllerPos);
        var display = (AccDisplayBlockEntity)ctx.getLevel().getBlockEntity(displayPos);
        var target = new com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryNode(
                "acc_display::world::" + displayPos.asLong(),
                com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryKind.DISPLAY, "world",
                "createthrusters:acc_display_panel", "Projector", null, displayPos);
        var data = AdvancedGraphNodeFactory.createDefaultData("acc_hologram_widget", null);
        data.put("TargetData", target.toTag());
        ListTag elements = new ListTag(); CompoundTag button = new CompoundTag();
        button.putString("Type", "button"); button.putString("InteractionId", "projected_button");
        button.putInt("W", 60); button.putInt("H", 20); elements.add(button); data.put("WidgetElements", elements);
        var hologram = new AdvancedGraphDocument.Node("hologram", "acc_hologram_widget", "", 0, 0, data);
        var graph = new AdvancedGraphDocument(); graph.nodes().add(hologram);
        AdvancedHudInteractions.synchronize(hologram, graph.edges());
        var active = AdvancedContraptionControllerBlockEntity.class.getDeclaredField("activeGraph");
        active.setAccessible(true); active.set(controller, graph);
        var runtimeField = AdvancedContraptionControllerBlockEntity.class.getDeclaredField("graphRuntime");
        runtimeField.setAccessible(true); var runtime = (GraphRuntime)runtimeField.get(controller);
        var publish = AdvancedContraptionControllerBlockEntity.class.getDeclaredMethod("publishAccDisplayUpdates");
        publish.setAccessible(true);
        runtime.compile(graph); runtime.tick(graph, false); publish.invoke(controller);
        ctx.assertTrue("graph".equals(display.displayFrame().getString("State")), "A lone hologram did not bind its projector");
        ctx.assertTrue("widgets".equals(display.displayFrame().getString("Mode")), "Hologram layout did not reach the widget renderer");
        ctx.assertTrue(display.graph().nodes().size() == 1
                && "acc_hologram_widget".equals(display.graph().nodes().getFirst().type()),
                "Hologram publication depended on a normal display widget");
        ctx.assertTrue(display.targetsThisDisplay(display.graph().nodes().getFirst())
                && display.value("hologram", "visible").asBoolean(), "Published hologram was not visible on its projector");
        ctx.assertTrue(Math.abs(display.value("hologram", "offset_z").asNumber() - 0.25) < 0.001,
                "Hologram projection inputs were not published");
        graph.nodes().clear(); graph.setRevision(graph.revision() + 1);
        runtime.compile(graph); runtime.tick(graph, false); publish.invoke(controller);
        ctx.assertTrue("unbound".equals(display.displayFrame().getString("State")), "Removing the hologram did not release its projector");
        ctx.succeed();
    }
}
