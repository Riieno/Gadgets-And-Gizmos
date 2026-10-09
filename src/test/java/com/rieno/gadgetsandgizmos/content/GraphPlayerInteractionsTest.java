package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.content.advanced.GraphRuntime;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// Verify player ownership through normal input pulses, delays and personal HUD state
class GraphPlayerInteractionsTest{
    @BeforeAll static void bootstrap(){ ControllerTestBootstrap.bootstrap(); }
    @Test void releasingMovementKeysRetainsEachPlayersMouseControlSession() throws ReflectiveOperationException{
        var controller = mock(AdvancedContraptionControllerBlockEntity.class);
        var runtime = mock(GraphRuntime.class);
        var level = mock(Level.class);
        when(controller.getLevel()).thenReturn(level);
        var participants = AdvancedContraptionControllerBlockEntity.class.getDeclaredField("physicalInteractionParticipants");
        participants.setAccessible(true);
        participants.set(controller, new java.util.HashSet<UUID>());
        var graphRuntime = AdvancedContraptionControllerBlockEntity.class.getDeclaredField("graphRuntime");
        graphRuntime.setAccessible(true);
        graphRuntime.set(controller, runtime);
        doCallRealMethod().when(controller).handlePhysicalInteraction(any(), anyBoolean(), anyBoolean(), anyString());
        when(controller.hasPhysicalInteractionParticipant(any())).thenCallRealMethod();
        var first = mock(net.minecraft.server.level.ServerPlayer.class);
        var second = mock(net.minecraft.server.level.ServerPlayer.class);
        UUID firstId = UUID.randomUUID(), secondId = UUID.randomUUID();
        when(first.getUUID()).thenReturn(firstId);
        when(second.getUUID()).thenReturn(secondId);

        controller.handlePhysicalInteraction(first, true, false, "use");
        controller.handlePhysicalInteraction(second, true, true, "use");
        controller.handlePhysicalInteraction(first, true, false, "w");
        controller.handlePhysicalInteraction(first, false, false, "w");
        controller.handlePhysicalInteraction(second, false, true, "space");
        assertTrue(controller.hasPhysicalInteractionParticipant(firstId));
        assertTrue(controller.hasPhysicalInteractionParticipant(secondId));
        verify(runtime).enqueuePhysicalInteraction(first, true, false, "w");
        verify(runtime).enqueuePhysicalInteraction(first, false, false, "w");
        controller.handlePhysicalInteraction(first, false, false, "use");
        assertFalse(controller.hasPhysicalInteractionParticipant(firstId));
        assertTrue(controller.hasPhysicalInteractionParticipant(secondId));
        controller.handlePhysicalInteraction(second, false, true, "use");
        assertFalse(controller.hasPhysicalInteractionParticipant(secondId));
    }
    @Test void differentKeysOnTheSameControllerKeepTheirOwnHudPlayers(){
        var controller = mock(AdvancedContraptionControllerBlockEntity.class);
        var level = mock(Level.class);
        when(controller.getLevel()).thenReturn(level);
        when(level.getGameTime()).thenReturn(100L);
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        when(controller.graphInputPlayer(any(),eq(false))).thenReturn(second);
        var graph = new AdvancedGraphDocument();
        for(String key : java.util.List.of("first","second")){
            var input = node(key,"controller_channel_input",new CompoundTag());
            input.data().putString("BindingId",key);
            graph.nodes().add(input);
            graph.nodes().add(node("hud_"+key,"hud_element",new CompoundTag()));
            graph.edges().add(edge(key,"active","hud_"+key,"visible"));
        }
        GraphRuntime runtime = new GraphRuntime(controller);
        runtime.compile(graph);
        runtime.setBindingActive("first",true,first);
        runtime.setBindingActive("second",true,second);
        assertTrue(runtime.playerHudValues(first).inputs().get("hud_first:visible").asBoolean());
        assertFalse(runtime.playerHudValues(first).inputs().get("hud_second:visible").asBoolean());
        assertFalse(runtime.playerHudValues(second).inputs().get("hud_first:visible").asBoolean());
        assertTrue(runtime.playerHudValues(second).inputs().get("hud_second:visible").asBoolean());
    }
    @Test void retargetingAnInputDropsItsOldReleaseOwnerWhileRoleChangesRetainIt(){
        var controller = mock(AdvancedContraptionControllerBlockEntity.class);
        var level = mock(Level.class);
        when(controller.getLevel()).thenReturn(level);
        when(level.getGameTime()).thenReturn(100L);
        var graph = new AdvancedGraphDocument();
        var input = node("input", "discovered_target_input", new CompoundTag());
        input.data().putString("BindingId", "button");
        input.data().put("TargetData", target("original",new net.minecraft.core.BlockPos(1,2,3)));
        graph.nodes().add(input);
        GraphRuntime runtime = new GraphRuntime(controller);
        runtime.compile(graph);
        UUID player = UUID.randomUUID();
        runtime.setBindingActive("button",true,player);
        assertEquals(player,runtime.bindingInteractionPlayer("button",true));
        input.data().put("TargetData",target("changed-role",new net.minecraft.core.BlockPos(1,2,3)));
        graph.setRevision(graph.revision()+1);
        runtime.compile(graph);
        assertEquals(player,runtime.bindingInteractionPlayer("button",true));
        input.data().put("TargetData",target("replacement",new net.minecraft.core.BlockPos(4,5,6)));
        graph.setRevision(graph.revision()+1);
        runtime.compile(graph);
        assertNull(runtime.bindingInteractionPlayer("button",true));
    }
    @Test void scalarButtonChangesCarryOnlyTheirOwnPlayerToTheCamera(){
        var controller = mock(AdvancedContraptionControllerBlockEntity.class);
        var level = mock(Level.class);
        when(controller.getLevel()).thenReturn(level);
        AtomicLong tick = new AtomicLong(100);
        when(level.getGameTime()).thenAnswer(call -> tick.get());
        var value = new java.util.concurrent.atomic.AtomicReference<Double>(0D);
        when(controller.getGraphBindingValue("button")).thenAnswer(call -> value.get());
        UUID player = UUID.randomUUID();
        var input = node("input", "discovered_target_input", new CompoundTag());
        input.data().putString("BindingId", "button");
        when(controller.graphInputPlayer(input)).thenReturn(player);
        var graph = new AdvancedGraphDocument();
        graph.nodes().add(input);
        graph.nodes().add(node("pulse", "pulse_on_change", new CompoundTag()));
        graph.nodes().add(node("camera", "control_camera", new CompoundTag()));
        graph.edges().add(edge("input", "value", "pulse", "value"));
        graph.edges().add(edge("pulse", "exec", "camera", "exec"));
        GraphRuntime runtime = new GraphRuntime(controller);
        runtime.tick(graph, false);
        verify(controller, never()).controlGraphCamera(any(), any(), any());
        tick.incrementAndGet();
        value.set(15D);
        runtime.tick(graph, false);
        verify(controller).controlGraphCamera(any(), any(), eq(player));
    }
    @Test void delayedCameraPulsesRetainEachPlayer(){
        var controller = mock(AdvancedContraptionControllerBlockEntity.class);
        var level = mock(Level.class);
        AtomicLong tick = new AtomicLong(100);
        when(controller.getLevel()).thenReturn(level);
        when(level.getGameTime()).thenAnswer(call -> tick.get());
        var graph = new AdvancedGraphDocument();
        CompoundTag trigger = new CompoundTag();
        trigger.putString("Event", "button");
        graph.nodes().add(node("button", "event_trigger", trigger));
        graph.nodes().add(node("delay", "debounce", new CompoundTag()));
        graph.nodes().add(node("camera", "control_camera", new CompoundTag()));
        graph.edges().add(edge("button", "exec", "delay", "exec"));
        graph.edges().add(edge("delay", "exec", "camera", "exec"));
        GraphRuntime runtime = new GraphRuntime(controller);
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        runtime.enqueue("button", first);
        runtime.enqueue("button", second);
        runtime.tick(graph, false);
        verify(controller, never()).controlGraphCamera(any(), any(), any());
        tick.incrementAndGet();
        runtime.tick(graph, false);
        verify(controller).controlGraphCamera(any(), any(), eq(first));
        verify(controller).controlGraphCamera(any(), any(), eq(second));
    }
    @Test void togglingOnePlayersHudLeavesOtherPlayersAndSharedVariablesUnchanged(){
        var controller = mock(AdvancedContraptionControllerBlockEntity.class);
        var level = mock(Level.class);
        when(controller.getLevel()).thenReturn(level);
        when(level.getGameTime()).thenReturn(100L);
        var graph = new AdvancedGraphDocument();
        graph.variables().put("visible", AdvancedGraphDocument.Value.bool(false));
        CompoundTag trigger = new CompoundTag(); trigger.putString("Event", "button");
        CompoundTag variable = new CompoundTag(); variable.putString("Variable", "visible");
        CompoundTag constant = new CompoundTag(); constant.putBoolean("Value", true);
        graph.nodes().add(node("button", "event_trigger", trigger));
        graph.nodes().add(node("write", "variable_set", variable));
        graph.nodes().add(node("read", "variable_get", variable));
        graph.nodes().add(node("on", "constant_boolean", constant));
        graph.nodes().add(node("hud", "hud_element", new CompoundTag()));
        graph.edges().add(edge("button", "exec", "write", "exec"));
        graph.edges().add(edge("on", "value", "write", "value"));
        graph.edges().add(edge("read", "value", "hud", "visible"));
        GraphRuntime runtime = new GraphRuntime(controller);
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        runtime.enqueue("button", first);
        runtime.tick(graph, false);
        assertTrue(runtime.playerHudValues(first).inputs().get("hud:visible").asBoolean());
        assertFalse(runtime.playerHudValues(second).inputs().get("hud:visible").asBoolean());
        assertFalse(graph.variables().get("visible").asBoolean());
        GraphRuntime restored = new GraphRuntime(controller);
        restored.restoreShutdownSnapshot(runtime.withInteractionPlayer(first,runtime::createShutdownSnapshot));
        restored.compile(graph);
        assertTrue(restored.playerHudValues(first).inputs().get("hud:visible").asBoolean());
        assertFalse(restored.playerHudValues(second).inputs().get("hud:visible").asBoolean());
    }
    private static AdvancedGraphDocument.Node node(String id, String type, CompoundTag data){
        return new AdvancedGraphDocument.Node(id, type, "", 0, 0, data);
    }
    private static AdvancedGraphDocument.Edge edge(String from, String output, String to, String input){
        return new AdvancedGraphDocument.Edge(from + "-" + to, from, output, to, input);
    }
    private static CompoundTag target(String id,net.minecraft.core.BlockPos pos){
        return new com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryNode(id,
                com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryKind.UNKNOWN,"group","minecraft:stone","Target",null,pos).toTag();
    }
}
