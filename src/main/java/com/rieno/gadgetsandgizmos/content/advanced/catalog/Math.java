package com.rieno.gadgetsandgizmos.content.advanced.catalog;

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphCatalog;
import com.rieno.gadgetsandgizmos.lib.control.math.Vector3;

import java.util.List;

import static com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument.Value.number;
import static com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphMathValues.vector;
import static java.util.Map.of;

public class Math extends AdvancedGraphCatalog {
    public Math() {
        category("math");
        register("pack_vector",
            of("x", "number", "y", "number", "z", "number"),
            of("vector", "map"),
            (ctx, inputs, sink) -> {
                sink.output("vector", vector(
                    new Vector3(
                        inputs.get("x").asNumber(),
                        inputs.get("y").asNumber(),
                        inputs.get("z").asNumber()
                    )
                ));
            }).defaultDataFromMap(ctx -> of(
            "OutputType", "vector3",
            "OutputType_variants", List.of(
                "vector2",
                "vector3",
                "quaternion"
            )
        )).defaultData();
        register("unpack_vector", of("vector", "map"), of("x", "number", "y", "number", "z", "number"), (ctx, inputs, sink) -> {
            var vector = vector(inputs.get("vector"));
            sink
                .output("x", number(vector.x()))
                .output("y", number(vector.y()))
                .output("z", number(vector.z()))
            ;
        });

    }
}
