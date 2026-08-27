package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.INode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.graph.CustomGraphModelImpl;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.AbstractNodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import com.rieno.gadgetsandgizmos.compat.synaxis.SynaxisNamedEventCompat;
import com.rieno.gadgetsandgizmos.compat.synaxis.SynaxisNamedEventConfig;
import com.rieno.gadgetsandgizmos.compat.synaxis.SynaxisNamedEventLdNode;
import com.rieno.gadgetsandgizmos.compat.synaxis.SynaxisNamedEventReceiveLdNode;
import com.rieno.gadgetsandgizmos.compat.synaxis.SynaxisNamedEventSendLdNode;
import com.verr1.synaxis.content.blocks.circuit.ldgraph.CircuitLdGraph;
import com.verr1.synaxis.content.blocks.circuit.ldgraph.CircuitLdGraphBlueprintAdapter;
import com.verr1.synaxis.content.blocks.circuit.ldgraph.CircuitLdGraphNodeOptions;
import com.verr1.synaxis.foundation.cimulink.core.circuit.CircuitDefinition;
import com.verr1.synaxis.foundation.cimulink.core.circuit.NodeDef;
import com.verr1.synaxis.foundation.cimulink.game.circuit.CircuitBlueprint;
import com.verr1.synaxis.foundation.cimulink.game.circuit.GraphLayout;
import org.joml.Vector2f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Translate the optional Named Event visual nodes through Synaxis's otherwise closed blueprint adapter
@Pseudo
@Mixin(targets = "com.verr1.synaxis.content.blocks.circuit.ldgraph.CircuitLdGraphBlueprintAdapter", remap = false)
public abstract class SynaxisCircuitLdGraphBlueprintAdapterMixin {
    @Redirect(method = "fromBlueprint", require = 0,
            at = @At(value = "INVOKE", target = "Lcom/verr1/synaxis/content/blocks/circuit/ldgraph/CircuitLdGraphBlueprintAdapter;createComponentNode(Lcom/lowdragmc/lowdraglib2/nodegraphtookit/model/graph/CustomGraphModelImpl;Lcom/verr1/synaxis/foundation/cimulink/game/circuit/GraphLayout;Lcom/verr1/synaxis/foundation/cimulink/core/circuit/NodeDef;[I)Lcom/lowdragmc/lowdraglib2/nodegraphtookit/model/node/NodeModel;"))
    private static NodeModel createthrusters$createNamedEventComponentNode(
            CustomGraphModelImpl graph, GraphLayout layout, NodeDef definition, int[] grid) {
        if (definition.type().equals(SynaxisNamedEventCompat.SEND_ID)) {
            return createNamedEventNode(graph, layout, definition, grid,
                    new SynaxisNamedEventSendLdNode());
        }
        if (definition.type().equals(SynaxisNamedEventCompat.RECEIVE_ID)) {
            return createNamedEventNode(graph, layout, definition, grid,
                    new SynaxisNamedEventReceiveLdNode());
        }
        return SynaxisCircuitLdGraphBlueprintAdapterAccessor.createthrusters$createComponentNode(
                graph, layout, definition, grid);
    }

    @Inject(method = "toBlueprint", at = @At("RETURN"), cancellable = true, require = 0)
    private static void createthrusters$storeNamedEventComponents(CircuitLdGraph graph,
                                                                   CallbackInfoReturnable<CircuitBlueprint> callback) {
        CircuitBlueprint blueprint = callback.getReturnValue();
        Map<String, NodeDef> replacements = new HashMap<>();
        for (INode node : graph.getNodes()) {
            AbstractNodeModel model = node.getNodeModel();
            if (!(model instanceof NodeModel nodeModel)) {
                continue;
            }
            if (node instanceof SynaxisNamedEventSendLdNode) {
                replacements.put(nodeModel.getName(), new NodeDef(
                        new com.verr1.synaxis.foundation.cimulink.core.circuit.NodeId(nodeModel.getName()),
                        SynaxisNamedEventCompat.SEND_ID,
                        new SynaxisNamedEventConfig(SynaxisNamedEventLdNode.topic(nodeModel))));
            } else if (node instanceof SynaxisNamedEventReceiveLdNode) {
                replacements.put(nodeModel.getName(), new NodeDef(
                        new com.verr1.synaxis.foundation.cimulink.core.circuit.NodeId(nodeModel.getName()),
                        SynaxisNamedEventCompat.RECEIVE_ID,
                        new SynaxisNamedEventConfig(SynaxisNamedEventLdNode.topic(nodeModel))));
            }
        }
        if (replacements.isEmpty()) {
            return;
        }
        CircuitDefinition definition = blueprint.definition();
        List<NodeDef> nodes = new ArrayList<>(definition.nodes().size());
        for (NodeDef node : definition.nodes()) {
            nodes.add(replacements.getOrDefault(node.id().value(), node));
        }
        callback.setReturnValue(new CircuitBlueprint(blueprint.formatVersion(), blueprint.name(),
                new CircuitDefinition(definition.publicInputs(), definition.publicOutputs(), nodes,
                        definition.links()), blueprint.layout()));
    }

    // Recreate one bridge node with the same saved position and its persisted Named Event topic
    private static NodeModel createNamedEventNode(CustomGraphModelImpl graph, GraphLayout layout,
                                                   NodeDef definition, int[] grid,
                                                   SynaxisNamedEventLdNode node) {
        GraphLayout.Position saved = layout.nodePositions().get(definition.id());
        Vector2f position = saved == null
                ? new Vector2f(grid[0] * 32.0F, grid[1] * 32.0F)
                : new Vector2f((float) saved.x() * 32.0F, (float) saved.y() * 32.0F);
        NodeModel model = graph.createNodeModel(node, position);
        if (definition.config() instanceof SynaxisNamedEventConfig config) {
            CircuitLdGraphNodeOptions.setStringOption(model, SynaxisNamedEventLdNode.TOPIC_OPTION,
                    config.name());
        }
        return model;
    }
}
