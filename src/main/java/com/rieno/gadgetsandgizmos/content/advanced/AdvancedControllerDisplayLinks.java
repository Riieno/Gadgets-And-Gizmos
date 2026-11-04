package com.rieno.gadgetsandgizmos.content.advanced;

import com.rieno.gadgetsandgizmos.lib.display.DisplayLinkInbox;
import com.rieno.gadgetsandgizmos.lib.graph.GraphPortOrder;
import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;

import java.util.LinkedHashMap;
import java.util.Map;

// Bind incoming Create streams to the ACC's saved output sections
public final class AdvancedControllerDisplayLinks{
    public static final String TYPE = "display_link_input";
    public static final String BINDINGS = "DisplayLinkBindings";
    private AdvancedControllerDisplayLinks(){}

    public static boolean synchronize(AdvancedGraphDocument.Node node, DisplayLinkInbox inbox){
        if(!TYPE.equals(node.type())) return false;
        var layout = inbox.layout();
        CompoundTag outputs = new CompoundTag(), labels = new CompoundTag(), bindings = new CompoundTag(), sections = new CompoundTag();
        layout.ports().forEach(outputs::putString); layout.labels().forEach(labels::putString);
        layout.bindings().forEach((port, entries) -> {
            var entry = entries.getFirst();
            CompoundTag value = new CompoundTag(); value.putString("Link", entry.targetId()); value.putString("Field", entry.portId());
            bindings.put(port, value);
        });
        for(var section : layout.sections()){
            CompoundTag value = new CompoundTag(); value.putString("Label", section.label());
            ListTag ports = new ListTag(); section.ports().forEach(port -> ports.add(StringTag.valueOf(port)));
            value.put("Ports", ports); sections.put(section.id(), value);
        }
        if(outputs.equals(node.data().getCompound("DynamicOutputs")) && bindings.equals(node.data().getCompound(BINDINGS))
                && sections.equals(node.data().getCompound("PortSections").getCompound("Outputs"))) return false;
        node.data().put("DynamicOutputs", outputs); node.data().put("OutputLabels", labels); node.data().put(BINDINGS, bindings);
        CompoundTag sides = node.data().getCompound("PortSections"); sides.put("Outputs", sections); node.data().put("PortSections", sides);
        GraphPortOrder.write(node.data(), GraphPortOrder.OUTPUT_ORDER, layout.ports().keySet());
        return true;
    }

    public static AdvancedGraphDocument.Value read(AdvancedGraphDocument.Node node, DisplayLinkInbox inbox, String port){
        if("data".equals(port)){
            Map<String, Object> streams = new LinkedHashMap<>();
            for(var snapshot : inbox.streams()){
                streams.put(snapshot.id().toString(), Map.of("text", snapshot.text(), "lines", snapshot.lines(),
                        "target_line", snapshot.targetLine(), "source_name", snapshot.sourceName(),
                        "source_type", snapshot.sourceType(), "updated_tick", snapshot.updatedTick()));
            }
            return GraphRuntime.fromLibraryValue(GraphValue.map(streams));
        }
        CompoundTag binding = node.data().getCompound(BINDINGS).getCompound(port);
        GraphValue value = inbox.read(binding.getString("Link"), binding.getString("Field"));
        return value == null ? AdvancedGraphDocument.Value.string("") : GraphRuntime.fromLibraryValue(value);
    }
}
