package com.rieno.gadgetsandgizmos.neoforge.network;

import com.rieno.gadgetsandgizmos.content.ControllerManifestStore;
import com.rieno.gadgetsandgizmos.lib.graph.SourcedGraphCatalog;
import com.rieno.gadgetsandgizmos.lib.menuconfig.MenuConfigTarget;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SharedGraphSourceTest{
    @Test
    void integratedServersDoNotDuplicateClientFilesAndDedicatedServersKeepTheirOwnSource() throws Exception{
        var file = new ControllerManifestStore.SharedGraphEntry("steering", "Steering");
        var target = MenuConfigTarget.of(new BlockPos(1, 2, 3), UUID.randomUUID());
        var graph = new CompoundTag();graph.putString("Name", "Loaded graph");
        var send = AdvancedContraptionControllerGraphPayload.class.getDeclaredMethod("sendSharedGraphs",
                IPayloadContext.class, MenuConfigTarget.class, String.class, String.class, CompoundTag.class);
        send.setAccessible(true);
        for(boolean dedicated : new boolean[]{false, true}){
            var context = mock(IPayloadContext.class);
            var player = mock(ServerPlayer.class);
            var server = mock(MinecraftServer.class);
            when(context.player()).thenReturn(player);
            when(player.getServer()).thenReturn(server);
            when(server.isDedicatedServer()).thenReturn(dedicated);
            try(var store = mockStatic(ControllerManifestStore.class);var packets = mockStatic(PacketDistributor.class)){
                store.when(ControllerManifestStore::listSharedGraphs).thenReturn(List.of(file));
                send.invoke(null, context, target, "Loaded shared graph", "conflict", graph);
                var sent = ArgumentCaptor.forClass(AdvancedControllerSharedGraphsPayload.class);
                packets.verify(() -> PacketDistributor.sendToPlayer(eq(player), sent.capture()));
                var reply = sent.getValue();
                var entries = SourcedGraphCatalog.merge(List.of(file), reply.entries(),
                        ControllerManifestStore.SharedGraphEntry::id, ControllerManifestStore.SharedGraphEntry::name);
                assertEquals(dedicated ? List.of("[C] Steering", "[S] Steering") : List.of("[C] Steering"),
                        entries.stream().map(SourcedGraphCatalog.Entry::label).toList());
                assertEquals(target.pos(), reply.pos());
                assertEquals(target.subLevelId(), reply.subLevelId());
                assertEquals("Loaded shared graph", reply.message());
                assertEquals("conflict", reply.conflictingName());
                assertEquals(graph, reply.graph());
                if(!dedicated) store.verifyNoInteractions();
            }
        }
    }
}
