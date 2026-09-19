package com.rieno.gadgetsandgizmos.graph.compile;

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.graph.compile.debug.DebugProps;
import com.rieno.gadgetsandgizmos.graph.compile.node_type.data.BlockData;
import net.minecraft.nbt.*;
import org.apache.commons.io.file.PathUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

class AbstractJVMGraphCompilerTest {

    AdvancedGraphDocument doc = new AdvancedGraphDocument();

    public static final File debugDir = new File("build/test_out");

    static {
        try {
            for(File file : debugDir.listFiles()) {
                PathUtils.delete(file.toPath());
            }
        } catch(Exception e) {

        }
    }

    String graphClassSubName = null;
    protected void setClassSubName(String subName){
        this.graphClassSubName=subName;
    }

    public JVMGraphRuntime runtime = new JVMGraphRuntime(new DebugProps(debugDir) {
        static AtomicInteger counter = new AtomicInteger();

        @Override
        public String transformGraphName(String className, String hash) {
            String classPrefix = graphClassSubName;
            if(classPrefix == null) classPrefix = "";
            else classPrefix = "$" + classPrefix;
            return className + classPrefix + "$_" + counter.incrementAndGet()+"_";
        }
    });

    static {
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out)));
        TestTypeRegister.register();
    }


    public AdvancedGraphDocument.Node binary(String type, AdvancedGraphDocument.Node a, String portA, AdvancedGraphDocument.Node b, String portB) {
        var plus = node(type, "");
        connect(a, portA, plus, "a");
        connect(b, portB, plus, "b");
        return plus;
    }

    public void assertEquals(AdvancedGraphDocument.Value expected, AdvancedGraphDocument.Value actual) {
        if(expected == actual) return;

        if(expected != null) Assertions.assertNotNull(actual, "Actual is null");
        if(expected == null) {
            Assertions.assertNull("Actual isnot null");
            return;
        }
        if(expected.type().equals("number")) {
            Assertions.assertEquals(expected.asNumber(), actual.asNumber(), 0.01, "Result value mismatch");
        } else {
            Assertions.assertEquals(expected.payload(), actual.payload(), "Result value mismatch");
        }
    }

    public void connect(AdvancedGraphDocument.Node outNode, String outPort,
                        AdvancedGraphDocument.Node inNode, String inPort
    ) {

        doc.edges().add(new AdvancedGraphDocument.Edge(
            uuid(),
            outNode.id(),
            outPort,
            inNode.id(),
            inPort
        ));
    }

    public void execConnect(AdvancedGraphDocument.Node out, AdvancedGraphDocument.Node in) {
        connect(out, "exec", in, "exec");
    }

    public AdvancedGraphDocument.@NotNull Node value(Tag value) {
        return node("const", "", Map.of("value", value));
    }

    public AdvancedGraphDocument.@NotNull Node value(double value) {
        return node("const", "", Map.of("value", DoubleTag.valueOf(value)));
    }

    public void addPorts(String pos, String... ports) {
        ListTag value = new ListTag();
        CompoundTag compoundTag = new CompoundTag();
        BlockData.WORLD.put(pos, compoundTag);
        compoundTag.put("ports", value);
        for(String port : ports) {
            value.add(StringTag.valueOf(port));
        }
    }

    public AdvancedGraphDocument.@NotNull Node getData(String pos) {
        return node("get_data", "", Map.of(BlockData.blockPosKey, StringTag.valueOf(pos)));
    }

    public AdvancedGraphDocument.@NotNull Node setData(String pos) {
        return node("set_data", "", Map.of(BlockData.blockPosKey, StringTag.valueOf(pos)));
    }

    public AdvancedGraphDocument.@NotNull Node tick() {
        return node("tick", "");
    }

    public AdvancedGraphDocument.@NotNull Node gate() {
        return node("gate", "");
    }

    public AdvancedGraphDocument.Node node(String type, String label) {
        return node(type, label, new CompoundTag());
    }

    public AdvancedGraphDocument.Node node(String type, String label, Map<String, Tag> map) {
        CompoundTag data = new CompoundTag();
        map.forEach(data::put);
        return node(type, label, data);
    }

    public AdvancedGraphDocument.Node node(String type, String label, CompoundTag data) {
        AdvancedGraphDocument.Node e = new AdvancedGraphDocument.Node(uuid(), type, label, 0, 0, data);
        doc.nodes().add(e);
        return e;
    }

    public AdvancedGraphDocument.Value output(AdvancedGraphDocument.Node node, String port) {

        return runtime.previewOutput(doc, node, port, true);
    }

    public String uuid() {
        return UUID.randomUUID().toString();
    }

}