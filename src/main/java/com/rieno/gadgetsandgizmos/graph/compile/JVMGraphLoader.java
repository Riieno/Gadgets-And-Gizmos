package com.rieno.gadgetsandgizmos.graph.compile;

import com.rieno.gadgetsandgizmos.graph.compile.debug.DebugProps;
import lombok.RequiredArgsConstructor;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
/**
 * Class loader becomes eligible for unloading when it is unreachable
 * along with all classes loaded by it.
 *
 * <p>Classes loaded by this loader may be unloaded by the JVM once neither
 * the loader nor any of its loaded classes are reachable from GC roots.</p>
 * */
@RequiredArgsConstructor
public class JVMGraphLoader extends ClassLoader {
    ConcurrentHashMap<String,byte[]> nameToBytes =new ConcurrentHashMap<>();
    final DebugProps debugProps;

    public void defineClass(String className, byte[] bytecode){
        nameToBytes.put(className, bytecode);
        if(debugProps!=null){
            debugProps.onDefineClass(className,bytecode);
        }
    }
    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        byte[] bytes = nameToBytes.get(name);
        if(bytes==null)
            throw new ClassNotFoundException(name);
        return defineClass(name,bytes,0,bytes.length);
    }
}
