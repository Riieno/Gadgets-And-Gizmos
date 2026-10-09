package com.rieno.gadgetsandgizmos.content.advanced;

import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerBlockEntity;
import com.rieno.gadgetsandgizmos.content.CameraBlockEntity;
import com.rieno.gadgetsandgizmos.lib.view.ViewRaycast;
import com.rieno.gadgetsandgizmos.lib.view.ViewRig;
import net.minecraft.nbt.CompoundTag;

import java.util.Locale;
import java.util.function.Function;

// Adapt ACC values to the camera's library-backed configuration
public final class CameraGraphNodes{
    // Prevent construction of the node adapter
    private CameraGraphNodes(){}
    // Apply configuration and export the requested ray result
    public static AdvancedGraphDocument.Value value(AdvancedContraptionControllerBlockEntity controller,
            AdvancedGraphDocument.Node node, String port, Function<String, AdvancedGraphDocument.Value> input,
            boolean simulation){
        CameraBlockEntity camera = configure(controller, node, input, simulation);
        return camera == null ? unavailable(port) : GraphRuntime.fromLibraryValue(camera.graphValue(port));
    }
    // Apply controls even when no ray output is connected or inspected
    public static CameraBlockEntity configure(AdvancedContraptionControllerBlockEntity controller,
            AdvancedGraphDocument.Node node, Function<String, AdvancedGraphDocument.Value> input,
            boolean simulation){
        CameraBlockEntity camera = controller == null || simulation ? null
                : controller.graphCamera(node, input.apply("target"));
        if(camera == null) return null;
        ViewRig.Mode mode;
        ViewRig.Orientation orientation;
        try{ mode = ViewRig.Mode.valueOf(input.apply("mode").asString().toUpperCase(Locale.ROOT)); }
        catch(IllegalArgumentException err){ mode = ViewRig.Mode.LOCKED; }
        try{ orientation = ViewRig.Orientation.valueOf(input.apply("orientation_mode").asString().toUpperCase(Locale.ROOT)); }
        catch(IllegalArgumentException err){ orientation = ViewRig.Orientation.LOCAL; }
        camera.configure(input.apply("max_ray_length").asNumber(), input.apply("rays_per_second").asNumber(),
                ViewRaycast.Filter.fromValue(GraphRuntime.toLibraryValue(input.apply("filter")),
                        "allowlist".equalsIgnoreCase(input.apply("filter_type").asString())),
                mode, orientation, input.apply("pan").asNumber(), input.apply("tilt").asNumber());
        return camera;
    }
    // Return stable output types when a camera is unloaded or a graph is simulated
    public static AdvancedGraphDocument.Value unavailable(String port){
        return switch(port){
            case "ray_cast" -> AdvancedGraphDocument.Value.list(new CompoundTag());
            case "hit_details" -> AdvancedGraphDocument.Value.map(new CompoundTag());
            case "hit", "is_sub_level" -> AdvancedGraphDocument.Value.bool(false);
            default -> AdvancedGraphDocument.Value.number(0);
        };
    }
}
