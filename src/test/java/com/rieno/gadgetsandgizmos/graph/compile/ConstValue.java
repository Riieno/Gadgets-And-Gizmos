package com.rieno.gadgetsandgizmos.graph.compile;

import com.rieno.gadgetsandgizmos.graph.compile.asm.JVMNodeType;
import com.rieno.gadgetsandgizmos.graph.compile.asm.Inputs;
import com.rieno.gadgetsandgizmos.graph.compile.asm.Outputs;
import com.rieno.gadgetsandgizmos.graph.compile.asm.ValueType;
import com.rieno.gadgetsandgizmos.graph.compile.util.GeneratorHelper;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.nbt.*;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import java.util.Map;

class ConstValue extends JVMNodeType {

    public ConstValue() {super(Map.of(), Map.of("value", ValueType.VALUE));}

    @Override
    public void compile(GeneratorHelper mv, Inputs inputs, Outputs outputs, CompoundTag data) {
        data.get("value").accept(new MyTagVisitor(mv));
        outputs.store(mv,"value");
    }

    private static class MyTagVisitor implements TagVisitor {
        private final GeneratorAdapter mv;

        public MyTagVisitor(GeneratorAdapter mv) {this.mv = mv;}

        @Override
        public void visitString(StringTag tag) {
            mv.visitLdcInsn(tag.getAsString());
        }

        @Override
        public void visitByte(ByteTag tag) {
            mv.push(tag.getAsByte());
        }

        @Override
        public void visitShort(ShortTag tag) {
            mv.push(tag.getAsShort());
        }

        @Override
        public void visitInt(IntTag tag) {
            mv.push(tag.getAsInt());
        }

        @Override
        public void visitLong(LongTag tag) {
            mv.push(tag.getAsLong());
        }

        @Override
        public void visitFloat(FloatTag tag) {
            mv.push(tag.getAsFloat());
        }

        @Override
        public void visitDouble(DoubleTag tag) {
            mv.push(tag.getAsDouble());
        }

        @Override
        public void visitByteArray(ByteArrayTag tag) {
            byte[] arr = tag.getAsByteArray();
            mv.push(arr.length);
            Type itemType = Type.BYTE_TYPE;
            mv.newArray(itemType);
            for(int i = 0; i < arr.length; i++) {
                mv.dup();
                mv.push(i);
                mv.push(arr[0]);
                mv.arrayStore(itemType);
            }
        }

        @Override
        public void visitIntArray(IntArrayTag tag) {
            var arr = tag.getAsIntArray();
            mv.push(arr.length);
            Type itemType = Type.INT_TYPE;
            mv.newArray(itemType);
            for(int i = 0; i < arr.length; i++) {
                mv.dup();
                mv.push(i);
                mv.push(arr[0]);
                mv.arrayStore(itemType);
            }
        }

        @Override
        public void visitLongArray(LongArrayTag tag) {
            var arr = tag.getAsLongArray();
            mv.push(arr.length);
            Type itemType = Type.LONG_TYPE;
            mv.newArray(itemType);
            for(int i = 0; i < arr.length; i++) {
                mv.dup();
                mv.push(i);
                mv.push(arr[0]);
                mv.arrayStore(itemType);
            }
        }

        @Override
        public void visitList(ListTag tag) {
            Type arrType = Type.getType(ObjectArrayList.class);
            mv.newInstance(arrType);
            mv.push(tag.size());
            mv.invokeConstructor(arrType,new Method("<init>","(I)V"));

        }

        @Override
        public void visitCompound(CompoundTag tag) {
throw new UnsupportedOperationException();
        }

        @Override
        public void visitEnd(EndTag tag) {
            throw new UnsupportedOperationException();
        }
    }
}
