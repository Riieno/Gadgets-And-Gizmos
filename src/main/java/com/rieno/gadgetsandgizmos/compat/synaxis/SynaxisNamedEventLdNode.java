package com.rieno.gadgetsandgizmos.compat.synaxis;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IOptionDefinitionContext;
import com.verr1.synaxis.content.blocks.circuit.ldgraph.CircuitLdGraphNodeOptions;
import com.verr1.synaxis.content.blocks.circuit.ldgraph.node.BusLdNode;
import com.verr1.synaxis.foundation.cimulink.game.component.BusConfig;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;

// Share the Synaxis bus-node port machinery while storing a Named Event topic alongside it
public abstract class SynaxisNamedEventLdNode extends BusLdNode {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final String TOPIC_OPTION = "named_event";

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Define the hidden fixed bus schema and the user-facing Named Event topic
    protected final void defineOptions(IOptionDefinitionContext context, BusConfig busConfig) {
        String encodedConfig = BusConfig.CODEC.encodeStart(NbtOps.INSTANCE, busConfig)
                .getOrThrow().toString();
        context.addOption("bus_config", TypeHandles.STRING)
                .withDefaultValue(encodedConfig)
                .showInInspectorOnly()
                .withoutConfigurator()
                .build();
        context.addOption(TOPIC_OPTION, TypeHandles.STRING)
                .withDisplayName(Component.literal("Named Event"))
                .withDefaultValue("")
                .build();
    }

    // Read the topic configured on one visual Synaxis node
    public static String topic(NodeModel model) {
        return CircuitLdGraphNodeOptions.stringOption(model, TOPIC_OPTION, "").strip();
    }
}
