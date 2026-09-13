package com.rieno.gadgetsandgizmos.graph.compile;

import com.rieno.gadgetsandgizmos.graph.compile.asm.Inputs;
import com.rieno.gadgetsandgizmos.graph.compile.asm.JVMNodeType;
import com.rieno.gadgetsandgizmos.graph.compile.asm.Outputs;
import com.rieno.gadgetsandgizmos.graph.compile.snapshot.SnapNode;
import com.rieno.gadgetsandgizmos.graph.compile.util.ClassNodeUtil;
import com.rieno.gadgetsandgizmos.graph.compile.util.GeneratorHelper;
import com.rieno.gadgetsandgizmos.graph.compile.util.Handle;
import com.rieno.gadgetsandgizmos.graph.compile.util.UnboundStateField;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.AllArgsConstructor;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.Lazy;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.lang.reflect.Field;

@AllArgsConstructor
public class LazyNodeProperties {
    public Class<? extends JVMNodeType> aClass;
    public final Lazy<Boolean> hasCustomControlFlow = Lazy.of(() -> {
        boolean hasCustomControlFlow1 = false;
        for(Class<? extends JVMNodeType> it = aClass; it != JVMNodeType.class; it = superClass(it)) {
            try {
                it.getDeclaredMethod("compileCustomControlFlow", GeneratorHelper.class, SnapNode.class, CompilationContext.class);
                hasCustomControlFlow1 = true;
                break;
            } catch(NoSuchMethodException e) {
            }
        }
        return hasCustomControlFlow1;
    });
    public final Lazy<Boolean> isModeSensitive = Lazy.of(() -> {
        Field calculatorMode = Handle.field(() -> CompilationContext.class.getDeclaredField("calculatorMode"));
        for(Class<? extends JVMNodeType> it = aClass; it != JVMNodeType.class; it = superClass(it)) {
            try {
                var method = JVMNodeType.class.getDeclaredMethod("compileOutputPortCalculations", GeneratorHelper.class, SnapNode.class, Inputs.class, Outputs.class, CompoundTag.class, CompilationContext.class);
                String name = it.getName();
                ClassNode classNode = ClassNodeUtil.getClassNode(name);
                String methodDescriptor = Type.getMethodDescriptor(method);
                for(MethodNode node : classNode.methods) {
                    if(node.name.equals(method.getName()) && node.desc.equals(methodDescriptor)) {
                        for(AbstractInsnNode instruction : node.instructions) {
                            if(instruction instanceof FieldInsnNode fieldInsnNode) {
                                if(fieldInsnNode.owner.equals(Type.getInternalName(calculatorMode.getDeclaringClass()))) {
                                    if(fieldInsnNode.name.equals(calculatorMode.getName())) {
                                        return true;
                                    }
                                }
                            }
                        }
                    }
                }
            } catch(Exception e) {
            }
        }
        return false;
    });

    public final Lazy<ObjectArrayList<Field>> stateFields = Lazy.of(() -> {
        ObjectArrayList<Field> fields = new ObjectArrayList<>();
        for(Class<?> it = aClass; it != JVMNodeType.class; it = it.getSuperclass()) {
            for(Field field : it.getDeclaredFields()) {
                if(field.getType() == UnboundStateField.class) {
                    fields.add(field);
                }
            }
        }
        return fields;
    });

    private static Class<? extends JVMNodeType> superClass(Class<? extends JVMNodeType> it) {
        return (Class<? extends JVMNodeType>) it.getSuperclass();
    }

}
