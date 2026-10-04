package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.lib.graph.GraphTargetPortLayout;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DataTargetOptionsTest {
    @Test
    void setDataKeepsDropdownOptionsUnderComposedPortIds() {
        var node = new AdvancedGraphDocument.Node("set", "set_block_data", "", 0, 0,
                new CompoundTag());
        var target = new GraphTargetPortLayout.Target("thruster", "Thruster", List.of(
                new GraphTargetPortLayout.Port("control_mode", "Control Mode", "string",
                        List.of("auto", "redstone", "computer"))));

        AdvancedContraptionControllerBlockEntity.applyDataTargetPortLayout(node, List.of(target), true);

        String port = node.data().getCompound("DynamicInputs").getAllKeys().iterator().next();
        assertEquals("string", node.data().getCompound("DynamicInputs").getString(port));
        assertEquals(List.of("auto", "redstone", "computer"),
                node.data().getCompound("InputOptions").getList(port, Tag.TAG_STRING).stream()
                        .map(Tag::getAsString).toList());
    }
}
