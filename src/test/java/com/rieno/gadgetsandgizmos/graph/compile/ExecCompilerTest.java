package com.rieno.gadgetsandgizmos.graph.compile;

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument.Value.number;

class ExecCompilerTest extends AbstractJVMGraphCompilerTest {


    @BeforeEach
    void setUp() {
        doc = new AdvancedGraphDocument();
    }

    @Test
    void test() {
        String block = "temp";
        addPorts(block,"a","b","c");

        var tick = tick();
        var setData=setData(block);
        var getData=getData(block);
        var num1=value(1);
        var num2=value(2);
        var num3=value(3);
        execConnect(tick,setData);
        connect(num1,"value",setData,"a");
        connect(num2,"value",setData,"b");
        connect(num3,"value",setData,"c");


        assertEquals(null,output(getData,"a"));
        assertEquals(null,output(getData,"b"));
        assertEquals(null,output(getData,"c"));
        AbstractJVMGraph jvmGraph = runtime.previewGraphOr(doc);
        jvmGraph.tick(0,"tick");

        assertEquals(null,output(getData,"a"));
        assertEquals(null,output(getData,"b"));
        assertEquals(null,output(getData,"c"));
        jvmGraph.passive(0,"passive");

        assertEquals(number(1),output(getData,"a"));
        assertEquals(number(2),output(getData,"b"));
        assertEquals(number(3),output(getData,"c"));
    }
}