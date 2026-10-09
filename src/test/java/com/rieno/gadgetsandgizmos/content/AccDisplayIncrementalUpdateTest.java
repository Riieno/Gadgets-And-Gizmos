package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.lib.display.ShipInformationDisplayModes;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccDisplayIncrementalUpdateTest{
    @BeforeAll
    static void bootstrap(){
        ControllerTestBootstrap.bootstrap();
    }

    @Test
    void fullGraphsAndValueDeltasPreserveChangesRemovalsAndForcedResync() throws Exception{
        AccDisplayBlockEntity display = mock(AccDisplayBlockEntity.class, CALLS_REAL_METHODS);
        doReturn(List.of()).when(display).getAllBehaviours();
        set(display, "configuredDisplayMode", AccDisplayBlockEntity.DISPLAY_MODE_AUTO);
        set(display, "shipInformationDisplayMode", ShipInformationDisplayModes.DEFAULT);
        set(display, "lastNetworkFrame", new CompoundTag());
        set(display, "lastNetworkGraphRevision", Integer.MIN_VALUE);
        CompoundTag frame = new CompoundTag();
        frame.putString("State", "graph");
        frame.putInt("GraphRevision", 3);
        CompoundTag graph = new CompoundTag();
        graph.putString("Node", "kept");
        frame.put("Graph", graph);
        CompoundTag values = new CompoundTag();
        values.putInt("speed", 4);
        values.putInt("old", 1);
        frame.put("Values", values);
        set(display, "displayFrame", frame);
        CompoundTag first = update(display);
        assertEquals(graph, first.getCompound("Graph"));
        assertFalse(first.getBoolean("ValueDelta"));
        first.getCompound("Graph").putString("Node", "packet edit");
        assertEquals("kept", graph.getString("Node"));
        values.putInt("speed", 8);
        values.remove("old");
        CompoundTag delta = update(display);
        assertFalse(delta.contains("Graph"));
        assertTrue(delta.getBoolean("ValueDelta"));
        assertEquals(8, delta.getCompound("Values").getInt("speed"));
        assertEquals("old", delta.getList("RemovedValues", net.minecraft.nbt.Tag.TAG_STRING).getString(0));
        assertEquals(0, update(display).getCompound("Values").size());
        set(display, "forceFullGraphSync", true);
        assertTrue(update(display).contains("Graph"));
        frame.putInt("GraphRevision", 4);
        assertTrue(update(display).contains("Graph"));
        frame.putString("State", "external");
        update(display);
        frame.putString("State", "graph");
        assertTrue(update(display).contains("Graph"));
    }

    private static CompoundTag update(AccDisplayBlockEntity display) throws Exception{
        var method = AccDisplayBlockEntity.class.getDeclaredMethod("writeIncrementalUpdate", HolderLookup.Provider.class);
        method.setAccessible(true);
        return ((CompoundTag) method.invoke(display, mock(HolderLookup.Provider.class))).getCompound("AccDisplayFrame");
    }

    private static void set(AccDisplayBlockEntity display, String name, Object val) throws Exception{
        Field field = AccDisplayBlockEntity.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(display, val);
    }
}
