package com.rieno.gadgetsandgizmos.graph.compile.node_type.data;

import com.rieno.gadgetsandgizmos.graph.compile.CompilationContext;
import com.rieno.gadgetsandgizmos.graph.compile.asm.*;
import com.rieno.gadgetsandgizmos.graph.compile.snapshot.SnapNode;
import com.rieno.gadgetsandgizmos.graph.compile.util.GeneratorHelper;
import com.rieno.gadgetsandgizmos.graph.compile.util.Handle;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import net.minecraft.nbt.CompoundTag;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public class SetData extends JVMNodeType {

    public SetData() {
        input("exec", ValueTypes.EXEC);
        output("exec", ValueTypes.EXEC);
    }

    @Override
    public Object2ObjectMap<String, ValueType> getInputPort(CompoundTag data, int nodeI) {
        return BlockData.mergePorts(super.inputPorts(data, nodeI), data);

    }
    @Override
    public void compileOutputPortCalculations(GeneratorHelper mv, SnapNode node, Inputs inputs, Outputs outputs, CompoundTag data, CompilationContext context) {
        if(node.inputs.length==1)return;
        String blockPos = data.getString(BlockData.blockPosKey);

        mv.push(blockPos);

        mv.push((node.inputs.length-1)<<1);
        mv.newArray(Type.getType(Object.class));

        for(int portI = 1; portI < node.inputs.length; portI++) {
            int i = (portI - 1) << 1;
            mv.dup();
            mv.push(i);
            String portName = node.portIndexerInverse[portI];
            mv.push(portName);
            mv.visitInsn(Opcodes.AASTORE);

            mv.dup();
            mv.push(i+1);
            inputs.load(mv,portName);
            mv.visitInsn(Opcodes.AASTORE);
        }
        mv.invoke(Handle.method(()->BlockData.class.getDeclaredMethod("setValues", String.class, Object[].class)));

    }

}
