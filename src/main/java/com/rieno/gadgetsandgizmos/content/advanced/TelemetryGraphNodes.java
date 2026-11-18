package com.rieno.gadgetsandgizmos.content.advanced;

import com.rieno.gadgetsandgizmos.lib.graph.GraphNodePresentationRegistry;
import com.rieno.gadgetsandgizmos.lib.physics.EntityTelemetryApi;
import com.rieno.gadgetsandgizmos.lib.view.ViewRaycast;
import net.minecraft.nbt.CompoundTag;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;

// Bind the shared telemetry schemas to the addon's camera and goggles nodes
public final class TelemetryGraphNodes{
    public static final String TRACKING_DETAILS = "tracking_details";
    private TelemetryGraphNodes(){}

    // Initialize new nodes and saved nodes with the same compact output sections
    public static void configure(String type, CompoundTag data){
        if("camera".equals(type)){
            var primary = new LinkedHashSet<>(ViewRaycast.ports().keySet());
            primary.remove("is_sub_level");
            GraphNodePresentationRegistry.mapOutputSections(data, "hit_details", ViewRaycast.detailPorts(),
                    ViewRaycast.detailSections(), primary);
        }else if("portable_tracker".equals(type)){
            var fields = new LinkedHashMap<>(AdvancedGraphCatalog.get(type).outputs());
            fields.remove(TRACKING_DETAILS);
            GraphNodePresentationRegistry.mapOutputSections(data, TRACKING_DETAILS, fields, EntityTelemetryApi.sections(),
                    new LinkedHashSet<>(List.of("available", TRACKING_DETAILS)));
            var labels = data.getCompound("OutputLabels"); labels.putString(TRACKING_DETAILS, "Tracking Details");
            data.put("OutputLabels", labels);
        }
    }
}
