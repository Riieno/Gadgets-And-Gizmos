package com.rieno.gadgetsandgizmos.graph.compile;

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.graph.compile.asm.ValueType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.Tag;
import org.apache.commons.io.file.PathUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.Map;
import java.util.UUID;

class JVMGraphCompilerTest {

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

    private JVMGraphRuntime runtime = new JVMGraphRuntime(debugDir);

    static {
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out)));
    }

    @BeforeAll
    static void beforeAll() {

        //JVMRegistry.register(new ConstValue());
        final ConstDValue value = new ConstDValue();
        JVMRegistry.register("const", value);
        JVMRegistry.register("+", new BinOpNode(Opcodes.DADD));
        JVMRegistry.register("-", new BinOpNode(Opcodes.DSUB));
        JVMRegistry.register("*", new BinOpNode(Opcodes.DMUL));
        JVMRegistry.register("/", new BinOpNode(Opcodes.DDIV));
        JVMRegistry.register("pow", BinaryNode.impl(ValueType.NUMBER, (mv, inputs, outputs, data) -> {
            inputs.load(mv, "a");
            inputs.load(mv, "b");
            mv.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                Type.getInternalName(Math.class),
                "pow", "(DD)D",
                false
            );

            outputs.store(mv, "c");
        }));
    }

    @BeforeEach
    void setUp() {
        doc = new AdvancedGraphDocument();
    }

    @Test
    void plus() {
        test(1, 2, "+", 3);
    }

    @Test
    void minus() {
        test(1, 2, "-", -1);
        test(2, 1, "-", 1);
    }

    @Test
    void mul() {
        test(2, 3, "*", 6);
    }

    @Test
    void div() {
        test(1, 2, "/", 1 / 2f);
        test(4, 2, "/", 2);
    }

    @Test
    void pow() {
        test(1, 2, "pow", 1);
        test(2, 3, "pow", 8);
    }
    @Test
    void multiple() {
        doc.setRevision(doc.revision() + 1);
        var one = value(1);
        var two = value(2);
        var three = value(2);

        var three2 = binary("+", one, "value", two, "value");
        var nine = binary("pow", three2, "c", three, "value");
        assertEquals(AdvancedGraphDocument.Value.number(9), runtime.previewOutput(doc, nine, "c"));
    }

    private AdvancedGraphDocument.Node binary(String type, AdvancedGraphDocument.Node a, String portA, AdvancedGraphDocument.Node b, String portB) {
        var plus = node(type, "");
        connect(a, portA, plus, "a");
        connect(b, portB, plus, "b");
        return plus;
    }

    private void test(double a, double b, String type, double c) {
        doc.setRevision(doc.revision() + 1);
        var one = value(DoubleTag.valueOf(a));
        var two = value(DoubleTag.valueOf(b));

        var plus = binary(type, one, "value", two, "value");
        assertEquals(AdvancedGraphDocument.Value.number(c), runtime.previewOutput(doc, plus, "c"));
    }

    private void assertEquals(AdvancedGraphDocument.Value expected, AdvancedGraphDocument.Value actual) {
        if(expected == actual) return;
        Assertions.assertEquals(expected.type(), actual.type(), "Result type mismatch");
        if(expected.type().equals("number")) {
            Assertions.assertEquals(expected.asNumber(), actual.asNumber(), 0.01, "Result value mismatch");
        } else {
            Assertions.assertEquals(expected.payload(), actual.payload(), "Result value mismatch");
        }
    }

    private void connect(AdvancedGraphDocument.Node outNode, String outPort,
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

    private AdvancedGraphDocument.@NotNull Node value(Tag value) {
        return node("const", "", Map.of("value", value));
    }
    private AdvancedGraphDocument.@NotNull Node value(double value) {
        return node("const", "", Map.of("value", DoubleTag.valueOf(value)));
    }

    private AdvancedGraphDocument.Node node(String type, String label) {
        return node(type, label, new CompoundTag());
    }

    private AdvancedGraphDocument.Node node(String type, String label, Map<String, Tag> map) {
        CompoundTag data = new CompoundTag();
        map.forEach(data::put);
        return node(type, label, data);
    }

    private AdvancedGraphDocument.Node node(String type, String label, CompoundTag data) {
        AdvancedGraphDocument.Node e = new AdvancedGraphDocument.Node(uuid(), type, label, 0, 0, data);
        doc.nodes().add(e);
        return e;
    }

    private String uuid() {
        return UUID.randomUUID().toString();
    }

}