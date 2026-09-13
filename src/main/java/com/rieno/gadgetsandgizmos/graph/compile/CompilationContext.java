package com.rieno.gadgetsandgizmos.graph.compile;

import com.rieno.gadgetsandgizmos.graph.struct.NodeCalculator;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
@RequiredArgsConstructor
public class CompilationContext {
    public static CompilationContext NULL;
    @NotNull
    public final JVMGraphCompiler.Cache compilationCache;
    @Nullable
    public NodeCalculator.Mode calculatorMode;

    private final Object2ObjectOpenHashMap<String,Object> map=new Object2ObjectOpenHashMap<>();
    public final TypeProperties typeProperties=new TypeProperties();

    public <T> T getProp(String key, T def){
        return ((T) map.getOrDefault(key, def));
    }
    public <T> void setProp(String key, T value){
        map.put(key, value);
    }

    public void reset() {
        map.clear();
        calculatorMode=null;
    }

    public static class TypeProperties {
        public boolean customControlFlow;
        public boolean modeSensitivePortCalculator;
    }
}
