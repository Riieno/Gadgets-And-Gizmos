package com.rieno.gadgetsandgizmos.content.advanced;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AdvancedHudStylePersistenceTest{
    @Test void oldStylesMigrateWithoutResettingColorsGeometryOrInteractionState(){
        CompoundTag elm = new CompoundTag();
        elm.putString("Type", "toggle"); elm.putString("Style", "tnt_brass");
        elm.putString("ToggleShape", "checkbox"); elm.putBoolean("Value", true);
        elm.putInt("AccentColor", 0xFFAA3366); elm.putInt("W", 126); elm.putInt("H", 28);
        elm.putString("InteractionId", "existing-control");
        AdvancedHudElementStyle.applyDefaults(elm);
        assertEquals("skeuomorphic_beveled", elm.getString("Style"));
        assertEquals("checkbox", elm.getString("ToggleShape"));
        assertTrue(elm.getBoolean("Value"));
        assertEquals(0xFFAA3366, elm.getInt("AccentColor"));
        assertEquals(126, elm.getInt("W"));
        assertEquals(28, elm.getInt("H"));
        assertEquals("existing-control", elm.getString("InteractionId"));
    }

    @Test void progressStylesAndGradientBindingsSurviveDocumentCopies(){
        var doc = new AdvancedGraphDocument();
        CompoundTag data = new CompoundTag();
        ListTag elements = new ListTag();
        CompoundTag elm = new CompoundTag();
        elm.putString("Type", "progress"); elm.putString("Style", "half_gradient");
        elm.putInt("W", 120); elm.putInt("H", 60); elm.putDouble("Min", 10); elm.putDouble("Max", 20);
        AdvancedHudElementStyle.applyDefaults(elm);
        AdvancedHudElementBinding.bind(elm, "GradientColor", "secondary_color");
        elements.add(elm); data.put("WidgetElements", elements);
        doc.nodes().add(new AdvancedGraphDocument.Node("hud", "advanced_hud_element", "", 0, 0, data));
        CompoundTag saved = doc.copy().nodes().getFirst().data().getList("WidgetElements", 10).getCompound(0);
        assertEquals("half_gradient", saved.getString("Style"));
        assertEquals("secondary_color", AdvancedHudElementBinding.port(saved, "GradientColor"));
        CompoundTag resolved = AdvancedHudElementBinding.resolvedCopy(saved,
                port -> AdvancedGraphDocument.Value.number(0xFFCCAA44));
        assertEquals(0xFFCCAA44, resolved.getInt("GradientColor"));
        assertEquals(10, saved.getDouble("Min"));
        assertEquals(20, saved.getDouble("Max"));
    }

    @Test void visualProgressDoesNotCreateInteractiveOutputPorts(){
        CompoundTag data = new CompoundTag();
        ListTag elements = new ListTag();
        for(String type : List.of("button", "toggle", "slider", "progress")){
            CompoundTag elm = new CompoundTag(); elm.putString("Type", type);
            AdvancedHudElementStyle.applyDefaults(elm); elements.add(elm);
            assertEquals("progress".equals(type) ? 14 : 6, AdvancedHudInteractiveStyles.options(type).size());
        }
        data.put("WidgetElements", elements);
        var node = new AdvancedGraphDocument.Node("hud", "advanced_hud_element", "", 0, 0, data);
        AdvancedHudInteractions.synchronize(node, new java.util.ArrayList<>());
        assertEquals(6, node.data().getCompound("DynamicOutputs").size());
        assertTrue(elements.getCompound(3).getString("ExecPort").isBlank());
        assertEquals("solid", elements.getCompound(3).getString("Style"));
    }
}
