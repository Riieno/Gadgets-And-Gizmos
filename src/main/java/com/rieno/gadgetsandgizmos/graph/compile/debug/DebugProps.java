package com.rieno.gadgetsandgizmos.graph.compile.debug;

import com.rieno.gadgetsandgizmos.graph.compile.JVMGraphLoader;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Lombok;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.SimpleVerifier;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceMethodVisitor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.List;

@Builder
@AllArgsConstructor
public class DebugProps {

    public File debugDir;

    public void onClassDefine(JVMGraphLoader loader, ClassNode node) {
        SimpleVerifier simpleVerifier = verifier(loader, node);

        Analyzer<BasicValue> analyzer = new Analyzer<>(simpleVerifier);
        MethodNode method0 = null;
        try {
            for(MethodNode method : node.methods) {
                method0 = method;
                method.maxLocals += 30;
                method.maxStack += 30;
                analyzer.analyze(node.name, method);
            }
        } catch(AnalyzerException e) {
            printMethod(method0);
            try {
                ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
                node.accept(writer);
                onDefineClass(node.name, writer.toByteArray());
                System.err.println("Saved");
            }catch(Exception ignore){

            }
            AnalyzerException t = new AnalyzerException(
                e.node,
                e.getMessage() + ": " + node.name,
                e.getCause()
            );
            t.setStackTrace(e.getStackTrace());
            throw Lombok.sneakyThrow(t);

        }
    }

    private void printMethod(MethodNode method) {

        var printer = new Textifier(Opcodes.ASM9) {
            public String tab2() {return tab2;}

            public String tab3() {return tab3;}

            public String ltab() {return ltab;}
        };
        method.accept(new TraceMethodVisitor(printer));
        int counter = -1;
        PrintStream err = System.err;
        err.println(method.name+" "+ method.desc);
        err.println();
        int maxPad = String.valueOf(method.instructions.size() + 1).length();
        for(Object o : printer.text) {
            if(o instanceof String s) {
                if(s.startsWith(printer.ltab()) && !s.startsWith(printer.tab3())) {
                    if(s.startsWith(printer.tab2())) {
                        String s1 = String.valueOf(counter);
                        err.print(" ".repeat(maxPad - s1.length()) + s1 + ":");
                    } else
                        err.print(" ".repeat(maxPad));

                    counter++;
                } else
                    err.print(" ".repeat(maxPad));
                err.print(s);
            } else {
                err.print(o);
            }
        }
    }

    private @NotNull SimpleVerifier verifier(JVMGraphLoader loader, ClassNode node) {
        SimpleVerifier simpleVerifier = new SimpleVerifier(
            Opcodes.ASM9,
            Type.getObjectType(node.name),
            Type.getObjectType(node.superName),
            List.of(),
            false
        ) {
            @Override
            protected boolean isSubTypeOf(BasicValue value, BasicValue expected) {
                try {
                    return super.isSubTypeOf(value, expected);
                } catch(Exception e) {
                    return false;
                }
            }
        };
        simpleVerifier.setClassLoader(loader);
        return simpleVerifier;
    }

    public void onDefineClass(String className, byte[] bytecode) {
        if(debugDir == null) return;
        ;
        debugDir.mkdir();
        try(FileOutputStream stream = new FileOutputStream(new File(debugDir, className + ".class"))) {
            stream.write(bytecode, 0, bytecode.length);
        } catch(IOException e) {
        }
    }
}
