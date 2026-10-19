package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphNodeFactory;
import com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryKind;
import com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryNode;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.List;
import java.util.Map;

@GameTestHolder("createthrusters")
@PrefixGameTestTemplate(false)
public final class GraphPresentationSaveGameTests{
    private GraphPresentationSaveGameTests(){}

    @GameTest(template = "empty_container", batch = "graph_save_presentation", timeoutTicks = 100)
    public static void savingAndApplyingRemovesHudWidgetsAndHologramsBeforeTheNextTick(GameTestHelper ctx){
        var fixture = fixture(ctx);
        var graph = new AdvancedGraphDocument();
        graph.nodes().add(element("hud_removed", "advanced_hud_element", null));
        graph.nodes().add(element("hud_kept", "advanced_hud_element", null));
        graph.nodes().add(element("widget_removed", "acc_display_widget", fixture.target()));
        graph.nodes().add(element("widget_kept", "acc_display_widget", fixture.target()));
        graph.nodes().add(element("hologram_removed", "acc_hologram_widget", fixture.target()));
        saveAndApply(ctx, fixture.controller(), graph);
        var firstHud = fixture.controller().activeGraphReadView();
        var firstDisplay = fixture.display().graph();
        ctx.assertTrue(firstHud.node("hud_removed") != null, "Initial HUD was not published");
        ctx.assertTrue(firstDisplay.nodes().stream().anyMatch(node -> "widget_removed".equals(node.id())),
                "Initial widget frame was not published by Apply");
        ctx.assertTrue(!fixture.controller().saveDraft(graph, fixture.controller().getDraftGraph().revision() + 10),
                "Stale save unexpectedly succeeded");
        ctx.assertTrue(firstHud == fixture.controller().activeGraphReadView(), "Failed save invalidated the active HUD");

        var edited = fixture.controller().getDraftGraph();
        edited.nodes().removeIf(node -> node.id().endsWith("_removed"));
        ctx.assertTrue(fixture.controller().saveDraft(edited, edited.revision()), "Edited graph could not be saved");
        ctx.assertTrue(firstHud != fixture.controller().activeGraphReadView(), "Save retained the old HUD topology cache");
        ctx.assertTrue(fixture.controller().applyDraft(), "Edited graph could not be applied");
        var currentHud = fixture.controller().activeGraphReadView();
        ctx.assertTrue(currentHud.node("hud_removed") == null && currentHud.node("hud_kept") != null,
                "Applied HUD did not remove only the deleted element");
        var currentDisplay = fixture.display().graph();
        ctx.assertTrue(currentDisplay != firstDisplay, "Apply retained the old display layout");
        ctx.assertTrue(currentDisplay.nodes().stream().noneMatch(node -> node.id().endsWith("_removed"))
                && currentDisplay.nodes().stream().anyMatch(node -> "widget_kept".equals(node.id())),
                "Deleted display widgets or holograms remained in the frame");
        var empty = fixture.controller().getDraftGraph();empty.nodes().clear();
        saveAndApply(ctx, fixture.controller(), empty);
        ctx.assertTrue(fixture.controller().activeGraphReadView().nodes().isEmpty(), "Last deleted HUD element remained");
        ctx.assertTrue("unbound".equals(fixture.display().displayFrame().getString("State"))
                && fixture.display().graph().nodes().isEmpty(), "Last deleted widget did not release its display");
        ctx.succeed();
    }

    @GameTest(template = "empty_container", batch = "graph_display_identity", timeoutTicks = 100)
    public static void replacementDocumentsWithTheSameRevisionRefreshClientWidgets(GameTestHelper ctx) throws Exception{
        var fixture = fixture(ctx);
        var old = new AdvancedGraphDocument();old.setRevision(4);
        old.nodes().add(element("old", "acc_display_widget", fixture.target()));
        var active = AdvancedContraptionControllerBlockEntity.class.getDeclaredField("activeGraph");active.setAccessible(true);
        active.set(fixture.controller(), old);
        fixture.display().receiveControllerUpdate(fixture.controller(), Map.of(), Map.of());
        var client = new AccDisplayBlockEntity(fixture.display().getBlockPos(), fixture.display().getBlockState());
        client.read(fixture.display().getUpdatePacket().getTag(), ctx.getLevel().registryAccess(), true);
        ctx.assertTrue(client.graph().nodes().stream().anyMatch(node -> "old".equals(node.id())), "Initial client widget is missing");

        var replacement = new AdvancedGraphDocument();replacement.setRevision(4);
        replacement.nodes().add(element("kept", "acc_display_widget", fixture.target()));
        active.set(fixture.controller(), replacement);
        fixture.display().receiveControllerUpdate(fixture.controller(), Map.of(), Map.of());
        CompoundTag packet = fixture.display().getUpdatePacket().getTag();
        ctx.assertTrue(packet.getCompound("AccDisplayFrame").contains("Graph"), "Same-revision replacement was sent as a value-only delta");
        client.read(packet, ctx.getLevel().registryAccess(), true);
        var layout = client.graph();
        ctx.assertTrue(layout.nodes().stream().noneMatch(node -> "old".equals(node.id()))
                && layout.nodes().stream().anyMatch(node -> "kept".equals(node.id())), "Client retained a deleted widget after graph replacement");
        fixture.display().receiveControllerUpdate(fixture.controller(), Map.of(),
                Map.of("kept:value", AdvancedGraphDocument.Value.number(0.5)));
        packet = fixture.display().getUpdatePacket().getTag();
        ctx.assertTrue(!packet.getCompound("AccDisplayFrame").contains("Graph"), "Value update unnecessarily resent the graph");
        client.read(packet, ctx.getLevel().registryAccess(), true);
        ctx.assertTrue(layout == client.graph(), "Value update rebuilt the retained client layout");
        ctx.succeed();
    }

    private static void saveAndApply(GameTestHelper ctx, AdvancedContraptionControllerBlockEntity controller,
                                     AdvancedGraphDocument graph){
        ctx.assertTrue(controller.saveDraft(graph, controller.getDraftGraph().revision()), "Graph save failed");
        ctx.assertTrue(controller.applyDraft(), "Graph apply failed: " + controller.getGraphDiagnostics());
    }

    private static AdvancedGraphDocument.Node element(String id, String type, ControllerDiscoveryNode target){
        CompoundTag data = AdvancedGraphNodeFactory.createDefaultData(type, null);
        CompoundTag defaults = data.getCompound("Defaults");
        defaults.put("visible", AdvancedGraphDocument.Value.bool(true).toTag());data.put("Defaults", defaults);
        ListTag elements = new ListTag();CompoundTag text = new CompoundTag();
        text.putString("Type", "text");text.putString("Text", id);text.putInt("W", 60);text.putInt("H", 20);
        elements.add(text);data.put("WidgetElements", elements);
        if(target != null) data.put("TargetData", target.toTag());
        return new AdvancedGraphDocument.Node(id, type, "", 0, 0, data);
    }

    private static Fixture fixture(GameTestHelper ctx){
        BlockPos controllerPos = ctx.absolutePos(new BlockPos(2, 3, 2));
        BlockPos displayPos = controllerPos.east(2);
        ctx.getLevel().setBlock(controllerPos, CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER.get().defaultBlockState(), 3);
        ctx.getLevel().setBlock(displayPos, CTBlocks.ACC_DISPLAY_PANEL.get().defaultBlockState(), 3);
        var target = new ControllerDiscoveryNode("acc_display::world::" + displayPos.asLong(),
                ControllerDiscoveryKind.DISPLAY, "world", "createthrusters:acc_display_panel", "Display", null, displayPos);
        return new Fixture((AdvancedContraptionControllerBlockEntity)ctx.getLevel().getBlockEntity(controllerPos),
                (AccDisplayBlockEntity)ctx.getLevel().getBlockEntity(displayPos), target);
    }

    private record Fixture(AdvancedContraptionControllerBlockEntity controller, AccDisplayBlockEntity display,
                           ControllerDiscoveryNode target){}
}
