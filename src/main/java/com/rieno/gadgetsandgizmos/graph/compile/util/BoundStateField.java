package com.rieno.gadgetsandgizmos.graph.compile.util;

import lombok.*;
import lombok.experimental.Accessors;
import org.objectweb.asm.Type;
import org.spongepowered.asm.mixin.gen.*;

@AllArgsConstructor
@EqualsAndHashCode
@With
@ToString
@Accessors(fluent=true)//record like getters
@Getter
public final class BoundStateField {
    final int nodeId;
    final String name;
    final String rawName;
    final Type type;

    public BoundStateField(int nodeId, String rawName, Type type) {
        this.nodeId = nodeId;
        this.rawName = rawName;
        this.type = type;
        name="f"+nodeId+"_"+rawName;
    }

}
