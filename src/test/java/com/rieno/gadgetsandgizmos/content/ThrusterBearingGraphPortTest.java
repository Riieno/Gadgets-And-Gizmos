package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphCatalog;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ThrusterBearingGraphPortTest {
    @BeforeAll
    static void bootstrap() {
        ControllerTestBootstrap.bootstrap();
    }

    @Test
    void setDataExposesSignedBearingControlAndDirectOutputsKeepValue() {
        Level level = mock(Level.class);
        BlockPos pos = BlockPos.ZERO;
        ThrusterBearingBlockEntity bearing = mock(ThrusterBearingBlockEntity.class);
        when(level.isLoaded(pos)).thenReturn(true);
        when(level.getBlockState(pos)).thenReturn(Blocks.STONE.defaultBlockState());
        when(level.getBlockEntity(pos)).thenReturn(bearing);
        when(bearing.getBlockState()).thenReturn(Blocks.STONE.defaultBlockState());
        when(bearing.graphWritableData()).thenReturn(Map.of("pivot_angle", "number"));

        assertEquals("number", AdvancedContraptionControllerBlockEntity
                .graphDataPorts(level, pos, true).getString("direct_signal"));
        for (String type : new String[] {"direct_target_output", "linker_face_output"}) {
            AdvancedGraphDocument.Node node = new AdvancedGraphDocument.Node(
                    "bearing", type, "", 0, 0, new CompoundTag());
            assertEquals("number", AdvancedGraphCatalog.inputs(node).get("value"));
        }
    }
}
