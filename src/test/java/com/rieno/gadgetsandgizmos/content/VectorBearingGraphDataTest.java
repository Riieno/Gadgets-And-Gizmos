package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;
import com.rieno.gadgetsandgizmos.lib.graph.GraphTargetPortLayout;
import com.rieno.gadgetsandgizmos.lib.kinetics.PropellerDirectionBehaviour;
import com.rieno.gadgetsandgizmos.lib.kinetics.PropellerThrustDirection;
import com.rieno.gadgetsandgizmos.lib.probe.BlockEntityDataAdapterRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VectorBearingGraphDataTest {
    private static final BlockPos POS = new BlockPos(3, 64, 5);
    private static final String DIRECTION = "propulsion_direction";

    @BeforeAll
    static void bootstrap(){
        ControllerTestBootstrap.bootstrap();
    }

    @Test
    void setDataSchemaIncludesPropulsionDropdown() throws Exception {
        var bearing = bearing();
        Level level = mock(Level.class);
        when(level.isLoaded(POS)).thenReturn(true);
        when(level.getBlockEntity(POS)).thenReturn(bearing);
        when(level.getBlockState(POS)).thenReturn(Blocks.STONE.defaultBlockState());

        CompoundTag ports = AdvancedContraptionControllerBlockEntity.graphDataPorts(level, POS, true);
        CompoundTag options = AdvancedContraptionControllerBlockEntity.graphDataPortOptions(level, POS, true);
        assertEquals("string", ports.getString(DIRECTION));
        assertEquals(List.of("Toward", "Away"), options.getList(DIRECTION, Tag.TAG_STRING).stream()
                .map(Tag::getAsString).toList());
        assertEquals("number", ports.getString("tilt_x"));
        assertEquals("boolean", ports.getString("keep_stable"));
    }

    @Test
    void graphWritesUpdateTheBodySelectorAndReadback() throws Exception {
        var bearing = bearing();
        assertTrue(BlockEntityDataAdapterRegistry.writeAll(bearing,
                Map.of(DIRECTION, GraphValue.string("Away"))));
        assertEquals("Away", BlockEntityDataAdapterRegistry.read(bearing, DIRECTION).value());
        var field = VectorBearingBlockEntity.class.getDeclaredField("thrustDirection");
        field.setAccessible(true);
        var direction = (PropellerDirectionBehaviour) field.get(bearing);
        assertEquals(PropellerThrustDirection.PUSH_WHEN_CLOCKWISE, direction.get());
        verify(bearing).setChanged();
        verify(bearing).sendData();

        assertFalse(BlockEntityDataAdapterRegistry.writeAll(bearing,
                Map.of(DIRECTION, GraphValue.string("invalid"))));
        assertEquals("Away", bearing.readGraphData(DIRECTION).asString());
        assertTrue(BlockEntityDataAdapterRegistry.writeAll(bearing,
                Map.of(DIRECTION, GraphValue.string("Toward"))));
        assertEquals(PropellerThrustDirection.PULL_WHEN_CLOCKWISE, direction.get());
        verify(bearing, times(2)).sendData();
    }

    @Test
    void composedSetDataKeepsTheVectorDirectionOptions() throws Exception {
        var bearing = bearing();
        var target = new GraphTargetPortLayout.Target("vector", "Vector Bearing", List.of(
                new GraphTargetPortLayout.Port(DIRECTION, "Propulsion Direction",
                        bearing.graphWritableData().get(DIRECTION),
                        bearing.graphWritableOptions().get(DIRECTION))));
        var node = new AdvancedGraphDocument.Node("set", "set_block_data", "", 0, 0,
                new CompoundTag());
        AdvancedContraptionControllerBlockEntity.applyDataTargetPortLayout(node, List.of(target), true);
        String port = node.data().getCompound("DynamicInputs").getAllKeys().iterator().next();
        assertEquals("string", node.data().getCompound("DynamicInputs").getString(port));
        assertEquals(List.of("Toward", "Away"),
                node.data().getCompound("InputOptions").getList(port, Tag.TAG_STRING).stream()
                        .map(Tag::getAsString).toList());
    }

    private static VectorBearingBlockEntity bearing() throws Exception {
        var bearing = mock(VectorBearingBlockEntity.class);
        when(bearing.getBlockState()).thenReturn(Blocks.STONE.defaultBlockState());
        when(bearing.graphReadableData()).thenCallRealMethod();
        when(bearing.graphWritableData()).thenCallRealMethod();
        when(bearing.graphWritableOptions()).thenCallRealMethod();
        when(bearing.readGraphData(anyString())).thenCallRealMethod();
        when(bearing.writeGraphData(anyString(), any(AdvancedGraphDocument.Value.class))).thenCallRealMethod();
        when(bearing.readGraphValue(anyString())).thenCallRealMethod();
        when(bearing.writeGraphValue(anyString(), any(GraphValue.class))).thenCallRealMethod();
        when(bearing.writeGraphValues(anyMap())).thenCallRealMethod();
        var field = VectorBearingBlockEntity.class.getDeclaredField("thrustDirection");
        field.setAccessible(true);
        field.set(bearing, new PropellerDirectionBehaviour(Component.literal("Direction"),
                bearing, "VectorThrustDirection"));
        return bearing;
    }
}
