package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphCatalog;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphNodeFactory;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphValidator;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphColorPlotNodesTest {
    @BeforeAll
    static void bootstrap() {
        ControllerTestBootstrap.bootstrap();
    }

    @Test
    void colorCanDrivePlotAndExistingWidgetColorFields() {
        AdvancedGraphDocument graph = new AdvancedGraphDocument();
        AdvancedGraphDocument.Node color = new AdvancedGraphDocument.Node("color", "constant_color", "", 0, 0,
                AdvancedGraphNodeFactory.createDefaultData("constant_color", AdvancedGraphNodeFactory.Context.EMPTY));
        AdvancedGraphDocument.Node plot = new AdvancedGraphDocument.Node("plot", "desmos_plot_point", "", 0, 0,
                AdvancedGraphNodeFactory.createDefaultData("desmos_plot_point", AdvancedGraphNodeFactory.Context.EMPTY));
        AdvancedGraphDocument.Node widget = new AdvancedGraphDocument.Node("widget", "acc_hologram_widget", "", 0, 0,
                new CompoundTag());
        graph.nodes().add(color);
        graph.nodes().add(plot);
        graph.nodes().add(widget);
        graph.edges().add(new AdvancedGraphDocument.Edge("plot-color", "color", "value", "plot", "color"));
        graph.edges().add(new AdvancedGraphDocument.Edge("widget-color", "color", "value", "widget", "color"));

        assertEquals("color", AdvancedGraphCatalog.outputs(color).get("value"));
        assertEquals("color", AdvancedGraphCatalog.inputs(plot).get("color"));
        assertEquals("color", AdvancedGraphCatalog.inputs(widget).get("color"));
        assertEquals("ignore", plot.data().getCompound("Defaults")
                .getCompound("x_functionality").getCompound("Payload").getString("Value"));
        assertTrue(AdvancedGraphValidator.validate(graph, true, true).valid());
    }
}
