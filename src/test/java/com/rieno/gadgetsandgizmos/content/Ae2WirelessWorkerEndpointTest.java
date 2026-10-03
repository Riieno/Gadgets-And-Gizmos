package com.rieno.gadgetsandgizmos.content;

import appeng.api.config.Actionable;
import appeng.api.implementations.blockentities.IWirelessAccessPoint;
import appeng.api.networking.IGrid;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.items.tools.powered.WirelessTerminalItem;
import com.rieno.gadgetsandgizmos.compat.ae2.Ae2WirelessWorkerEndpoint;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerResourceKey;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerResourcePacket;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerResourceType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class Ae2WirelessWorkerEndpointTest {
    @BeforeAll static void bootstrap(){ ControllerTestBootstrap.bootstrap(); }

    @Test void exposesAndExtractsNetworkFluidInMillibuckets() throws Exception {
        ServerLevel level = mock(ServerLevel.class);
        PlayerMannequinEntity worker = mock(PlayerMannequinEntity.class);
        ItemStackHandler tools = new ItemStackHandler(1);
        tools.setStackInSlot(0, new ItemStack(Items.STICK));
        ItemStack terminalStack = tools.getStackInSlot(0);
        when(worker.getUUID()).thenReturn(UUID.randomUUID());
        when(worker.blockPosition()).thenReturn(BlockPos.ZERO);
        when(worker.workerTools()).thenReturn(tools);
        when(level.getGameTime()).thenReturn(42L);

        WirelessTerminalItem terminal = mock(WirelessTerminalItem.class);
        when(terminal.getAECurrentPower(terminalStack)).thenReturn(100.0);
        IGrid grid = mock(IGrid.class);
        IStorageService service = mock(IStorageService.class);
        MEStorage storage = mock(MEStorage.class);
        IWirelessAccessPoint accessPoint = mock(IWirelessAccessPoint.class);
        AEFluidKey lava = AEFluidKey.of(Fluids.LAVA);
        KeyCounter stock = new KeyCounter();
        stock.add(lava, 3000L);
        when(grid.getStorageService()).thenReturn(service);
        when(service.getCachedInventory()).thenReturn(stock);
        when(storage.extract(eq(lava), eq(1000L), eq(Actionable.SIMULATE), any())).thenReturn(1000L);
        when(storage.extract(eq(lava), eq(1000L), eq(Actionable.MODULATE), any())).thenReturn(1000L);
        when(storage.insert(eq(lava), eq(500L), eq(Actionable.SIMULATE), any())).thenReturn(500L);

        Ae2WirelessWorkerEndpoint endpoint = new Ae2WirelessWorkerEndpoint(level, worker);
        Class<?> type = Class.forName(Ae2WirelessWorkerEndpoint.class.getName() + "$Connection");
        Constructor<?> constructor = type.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        Object connection = constructor.newInstance(0, terminalStack, terminal, grid, accessPoint, storage);
        Field tick = Ae2WirelessWorkerEndpoint.class.getDeclaredField("connectionTick");
        Field cached = Ae2WirelessWorkerEndpoint.class.getDeclaredField("connectionCache");
        tick.setAccessible(true);
        cached.setAccessible(true);
        tick.setLong(endpoint, 42L);
        cached.set(endpoint, connection);

        WorkerResourceKey resource = new WorkerResourceKey(WorkerResourceType.FLUID,
                ResourceLocation.withDefaultNamespace("lava"));
        assertEquals(3000L, endpoint.contents().get(resource));
        assertTrue(endpoint.canExtract(resource));
        assertEquals(1000L, endpoint.extract(resource, 1000L, true).amount());
        assertEquals(500L, endpoint.insert(new WorkerResourcePacket(resource, 500L, null), true));
        verify(storage).extract(eq(lava), eq(1000L), eq(Actionable.SIMULATE), any());
        verify(terminal, never()).extractAEPower(any(), anyDouble(), any());
        assertEquals(1000L, endpoint.extract(resource, 1000L, false).amount());
        verify(storage).extract(eq(lava), eq(1000L), eq(Actionable.MODULATE), any());
        verify(terminal).extractAEPower(eq(terminalStack), eq(1.0), eq(Actionable.MODULATE));
    }
}
