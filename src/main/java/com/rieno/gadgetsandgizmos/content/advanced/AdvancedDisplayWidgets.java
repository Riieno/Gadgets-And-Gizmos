package com.rieno.gadgetsandgizmos.content.advanced;

import com.rieno.gadgetsandgizmos.lib.display.WidgetCanvasProjection;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

// Connect designer canvases to ACC display nodes without overriding authored elements
public final class AdvancedDisplayWidgets{
    private static final Set<String> LEGACY_PORTS = Set.of("x", "y", "width", "height", "rotation", "scale",
            "font_size", "color", "background_color", "accent_color", "track_color", "border_color",
            "border_width", "border_radius", "minimum", "maximum");
    private AdvancedDisplayWidgets(){}

    public static void initialize(String type, CompoundTag data){
        data.putBoolean("DesignerWidget", true);
        data.remove("WidgetType");
        data.put("WidgetElements", new ListTag());
        CompoundTag defaults = data.getCompound("Defaults");
        LEGACY_PORTS.forEach(defaults::remove);
        addProjectionDefaults(type, data, defaults);
        data.put("Defaults", defaults);
        clearTitle(data);
    }

    public static void migrate(AdvancedGraphDocument.Node node, List<AdvancedGraphDocument.Edge> edges){
        CompoundTag data = node.data();
        clearTitle(data);
        edges.removeIf(edge -> node.id().equals(edge.toNode()) && "label".equals(edge.toPort()));
        if(data.getBoolean("DesignerWidget")) return;
        Set<String> connected = new HashSet<>();
        for(var edge : edges) if(node.id().equals(edge.toNode())) connected.add(edge.toPort());
        CompoundTag defaults = data.getCompound("Defaults");
        CompoundTag inputs = data.getCompound("DynamicInputs");
        ListTag elements = data.getList("WidgetElements", Tag.TAG_COMPOUND);
        for(int i = 0; i < elements.size(); i++){
            CompoundTag elm = elements.getCompound(i);
            if(!elm.getBoolean("ManagedWidget")) continue;
            CompoundTag resolved = AdvancedHudElementBinding.resolvedCopy(elm, port -> stored(defaults, port));
            CompoundTag bindings = elm.getCompound(AdvancedHudElementBinding.BINDINGS_TAG).copy();
            for(String property : Set.copyOf(bindings.getAllKeys())){
                String port = bindings.getString(property);
                if(!connected.contains(port)) bindings.remove(property);
                else inputs.putString(port, legacyType(port));
            }
            resolved.remove("ManagedWidget");
            resolved.put(AdvancedHudElementBinding.BINDINGS_TAG, bindings);
            elements.set(i, resolved);
        }
        for(String port : LEGACY_PORTS){
            if(connected.contains(port)) inputs.putString(port, legacyType(port));
        }
        data.put("WidgetElements", elements); data.put("DynamicInputs", inputs);
        addProjectionDefaults(node.type(), data, defaults);
        data.put("Defaults", defaults);
        data.remove("WidgetType"); data.putBoolean("DesignerWidget", true);
    }

    // Retire the container title without changing text authored inside the designer
    private static void clearTitle(CompoundTag data){
        data.remove("Label");
        for(String key : List.of("Defaults", "DynamicInputs", "InputLabels", "InputOptions", "HudFieldLabels")){
            data.getCompound(key).remove("label");
        }
        for(Tag raw : data.getList("WidgetElements", Tag.TAG_COMPOUND)){
            CompoundTag elm = (CompoundTag)raw;
            CompoundTag bindings = elm.getCompound(AdvancedHudElementBinding.BINDINGS_TAG);
            for(String property : Set.copyOf(bindings.getAllKeys())){
                if(!"label".equals(bindings.getString(property))) continue;
                AdvancedHudElementBinding.bind(elm, property, "");
                if("Text".equals(property)) elm.putString("Text", "");
            }
        }
    }

    private static String legacyType(String port){
        if("label".equals(port) || "image".equals(port)) return "string";
        return port.endsWith("color") ? "color" : "number";
    }

    private static void addProjectionDefaults(String type, CompoundTag data, CompoundTag defaults){
        if(!"acc_hologram_widget".equals(type)) return;
        put(defaults, "offset_x", 0); put(defaults, "offset_y", 0); put(defaults, "offset_z", 0.25);
        put(defaults, "size_x", Math.max(1, data.getInt("WidgetWidth")) / (double)com.rieno.gadgetsandgizmos.content.AccDisplayBlockEntity.PIXELS_PER_BLOCK);
        put(defaults, "size_y", Math.max(1, data.getInt("WidgetHeight")) / (double)com.rieno.gadgetsandgizmos.content.AccDisplayBlockEntity.PIXELS_PER_BLOCK);
        put(defaults, "projection_rotation", 0);
        CompoundTag labels = data.getCompound(AdvancedGraphCatalog.INPUT_LABELS_TAG);
        labels.putString("offset_x", "Offset Right (blocks)");
        labels.putString("offset_y", "Offset Up (blocks)");
        labels.putString("offset_z", "Offset Outward (blocks)");
        labels.putString("size_x", "Width (blocks)"); labels.putString("size_y", "Height (blocks)");
        labels.putString("projection_rotation", "Projection Rotation (degrees)");
        data.put(AdvancedGraphCatalog.INPUT_LABELS_TAG, labels);
    }

    private static void put(CompoundTag defaults, String port, double value){
        if(!defaults.contains(port)) defaults.put(port, AdvancedGraphDocument.Value.number(value).toTag());
    }

    private static AdvancedGraphDocument.Value stored(CompoundTag defaults, String port){
        CompoundTag encoded = defaults.getCompound(port);
        return new AdvancedGraphDocument.Value(encoded.getString("Type"), encoded.getCompound("Payload"));
    }

    // Preserve the layout's proportions when its display changes size
    public static void resizeCanvas(AdvancedGraphDocument.Node node, int width, int height){
        WidgetCanvasProjection.resizeCanvas(node.data(), width, height);
    }
}
