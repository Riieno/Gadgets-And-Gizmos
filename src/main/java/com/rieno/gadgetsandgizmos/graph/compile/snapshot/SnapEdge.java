package com.rieno.gadgetsandgizmos.graph.compile.snapshot;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Objects;

@AllArgsConstructor
@Getter
public class SnapEdge {
    public final SnapNode nodeA;
    public final int portA;
    public final SnapNode nodeB;
    public final int portB;
}
